package app.ai.chat.feature;

import app.ai.chat.history.ChatHistoryService;
import app.ai.rag.DocumentIngestionService;
import app.ai.rag.RagDocumentEntity;
import app.ai.rag.RagDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 문서 기반 Q&A (docs/doc-rag-design.md 6절): 두 스코프를 함께 검색해
 * {@link #augment} 훅으로 프롬프트 맨 앞에 주입한다.
 *
 * <ul>
 *   <li><b>공용 코퍼스</b> — chat_doc_chunk에 외부 파이프라인이 적재해 둔 문서
 *       (증권사 리포트 등, {@code sessionId} 메타데이터가 없다). 모든 세션이 참조한다.</li>
 *   <li><b>세션 첨부</b> — 이 세션에 업로드된 문서({@link DocumentIngestionService}가
 *       {@code sessionId}를 붙여 인덱싱). 업로드한 세션에서만 검색되고, 공용 검색
 *       결과에서는 후처리 필터로 배제되므로 다른 세션/사용자에게 절대 노출되지 않는다.</li>
 * </ul>
 *
 * <p>검색 실패는 대화를 막지 않는다: 실패 사실을 컨텍스트로 주입하고 스트리밍은 계속된다.
 */
@Service
public class DocQaService extends AbstractChatFeatureService {

    private static final Logger log = LoggerFactory.getLogger(DocQaService.class);

    private static final String SYSTEM_PROMPT = """
            You are a document Q&A assistant. A retrieval system supplies excerpts from a shared
            document repository and from files the user uploaded to this conversation.
            Ground every answer in those excerpts. If the excerpts cannot answer the question,
            say so plainly instead of guessing — never fabricate document content.
            End each answer with the source line "출처: <filename>" listing the documents
            you actually used. ALWAYS answer in Korean.
            """;

    private static final String CONTEXT_HEADER = """
            아래는 공용 문서 저장소와 사용자가 이 대화에 업로드한 문서에서 검색된 발췌다.
            답변은 반드시 이 발췌에 근거하고, 발췌로 답할 수 없으면 그렇다고 말하라.
            답변 끝에 "출처: 파일명" 형식으로 실제 사용한 근거 문서를 표기하라.
            """;

    private static final String SESSION_SECTION_HEADER = "\n== 이 대화에 업로드된 문서 발췌 ==\n";
    private static final String SHARED_SECTION_HEADER = "\n== 공용 문서 저장소 발췌 ==\n";

    private static final String NO_MATCH_NOTICE = """
            공용 문서 저장소와 이 세션에 업로드된 문서에서 질문과 관련된 내용을 찾지 못했다.
            그 사실을 말하고, 문서에 없는 내용을 지어내지 마라. 필요하면 관련 문서를
            업로드하라고 안내하라.
            """;

    private static final String SEARCH_FAILED_NOTICE = """
            문서 검색 시스템에 일시적인 오류가 발생해 문서를 참조할 수 없다. 그 사실을 알리고,
            문서 내용을 추측해 답하지 마라.
            """;

    /**
     * 공용 검색은 무필터 조회 후 세션 청크를 걸러내므로(아래 {@link #searchSharedCorpus}
     * 참고) 걸러질 몫을 감안해 여유분을 곱해 가져온다.
     */
    private static final int SHARED_OVERFETCH_FACTOR = 4;

    /** 공용 코퍼스(외부 적재) 청크의 출처 메타데이터 키 — 우리 청크의 filename에 대응. */
    private static final String META_SHARED_SOURCE = "source";

    private final VectorStore vectorStore;
    private final RagDocumentRepository ragDocumentRepository;
    private final int topK;
    private final double similarityThreshold;

    public DocQaService(ChatClient.Builder chatClientBuilder,
                        ChatHistoryService chatHistoryService,
                        VectorStore vectorStore,
                        RagDocumentRepository ragDocumentRepository,
                        @Value("${app.rag.top-k:4}") int topK,
                        @Value("${app.rag.similarity-threshold:0.30}") double similarityThreshold) {
        super(chatClientBuilder, chatHistoryService, SYSTEM_PROMPT);
        this.vectorStore = vectorStore;
        this.ragDocumentRepository = ragDocumentRepository;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
    }

    @Override
    protected List<Message> augment(String sessionId, String userMessage, List<Message> messages) {
        List<Message> augmented = new ArrayList<>();
        augmented.add(new SystemMessage(buildRetrievalContext(sessionId, userMessage)));
        augmented.addAll(messages);
        return augmented;
    }

    private String buildRetrievalContext(String sessionId, String userMessage) {
        List<Document> sessionResults;
        List<Document> sharedResults;
        try {
            sessionResults = searchSessionAttachments(sessionId, userMessage);
            sharedResults = searchSharedCorpus(userMessage);
        } catch (Exception e) {
            log.warn("문서 벡터 검색 실패 — 검색 없이 대화 계속 (session={}): {}", sessionId, e.getMessage());
            return SEARCH_FAILED_NOTICE;
        }

        if (sessionResults.isEmpty() && sharedResults.isEmpty()) {
            log.debug("문서 검색 결과 없음 (session={}, query={})", sessionId, userMessage);
            return NO_MATCH_NOTICE;
        }

        log.debug("문서 검색 주입: 세션 첨부 {}건 + 공용 {}건 (session={})",
                sessionResults.size(), sharedResults.size(), sessionId);
        StringBuilder context = new StringBuilder(CONTEXT_HEADER);
        int index = 1;
        if (!sessionResults.isEmpty()) {
            context.append(SESSION_SECTION_HEADER);
            index = appendExcerpts(context, sessionResults, index);
        }
        if (!sharedResults.isEmpty()) {
            context.append(SHARED_SECTION_HEADER);
            appendExcerpts(context, sharedResults, index);
        }
        return context.toString();
    }

    /** 세션 첨부 검색 — 항상 {@code sessionId} 필터. 다른 세션의 첨부는 절대 걸리지 않는다. */
    private List<Document> searchSessionAttachments(String sessionId, String userMessage) {
        if (!ragDocumentRepository.existsBySessionIdAndStatus(
                UUID.fromString(sessionId), RagDocumentEntity.Status.READY)) {
            return List.of();
        }
        List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
                .query(userMessage)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .filterExpression(new FilterExpressionBuilder()
                        .eq(DocumentIngestionService.META_SESSION_ID, sessionId)
                        .build())
                .build());
        return results == null ? List.of() : results;
    }

    /**
     * 공용 코퍼스 검색 — {@code sessionId} 메타데이터가 없는 청크만 대상. pgvector 필터
     * 표현식(jsonpath)은 "키가 없는 행"을 매칭하지 못하므로 무필터로 여유 있게 가져온 뒤
     * 자바에서 세션 청크를 제외한다. 어떤 세션의 첨부든(현재 세션 포함 — 첨부는 위
     * {@link #searchSessionAttachments}가 담당) 이 필터에서 탈락하므로, 한 사용자의
     * 첨부가 다른 사용자의 프롬프트에 섞이는 일은 구조적으로 불가능하다.
     */
    private List<Document> searchSharedCorpus(String userMessage) {
        List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
                .query(userMessage)
                .topK(topK * SHARED_OVERFETCH_FACTOR)
                .similarityThreshold(similarityThreshold)
                .build());
        if (results == null) {
            return List.of();
        }
        return results.stream()
                .filter(doc -> !doc.getMetadata().containsKey(DocumentIngestionService.META_SESSION_ID))
                .limit(topK)
                .toList();
    }

    private static int appendExcerpts(StringBuilder context, List<Document> docs, int startIndex) {
        int i = startIndex;
        for (Document doc : docs) {
            context.append('[').append(i++).append("] ")
                    .append(sourceName(doc))
                    .append('\n')
                    .append(doc.getText())
                    .append('\n');
        }
        return i;
    }

    private static String sourceName(Document doc) {
        Object name = doc.getMetadata().get(DocumentIngestionService.META_FILENAME);
        if (name == null) {
            name = doc.getMetadata().get(META_SHARED_SOURCE);
        }
        return name == null ? "unknown" : name.toString();
    }
}
