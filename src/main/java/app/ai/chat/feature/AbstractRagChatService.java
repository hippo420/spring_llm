package app.ai.chat.feature;

import app.ai.chat.dto.ChatStreamEvent;
import app.ai.chat.history.ChatHistoryService;
import app.ai.rag.DocumentRetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 문서 검색 증강 기능의 공통 베이스 (docs/06-doc-rag-design.md 6.3절):
 * {@link DocumentRetrievalService}의 이중 스코프(공용 코퍼스 + 세션 첨부) 검색 결과를
 * {@link #augment} 훅에서 SystemMessage로 프롬프트 맨 앞에 주입하고, 진행 상태(STATUS)·
 * 참조 문서(SOURCES) 이벤트를 발행한다. 서브클래스는 기능 고유의 시스템 프롬프트만
 * 정의한다 — {@link DocQaService}(항상 검색). {@link ToolRagService}는 여기 속하지
 * 않는다 — 매 질문 자동 검색 대신 모델이 도구로 소스를 선택한다.
 *
 * <p>검색 실패는 대화를 막지 않는다: 실패 사실을 컨텍스트로 주입하고 스트리밍은 계속된다.
 */
public abstract class AbstractRagChatService extends AbstractChatFeatureService {

    private static final Logger log = LoggerFactory.getLogger(AbstractRagChatService.class);

    private static final String CONTEXT_HEADER = """
            아래는 공용 문서 저장소와 사용자가 이 대화에 업로드한 문서에서 검색된 발췌다.
            답변은 반드시 이 발췌에 근거하고, 발췌로 답할 수 없으면 그렇다고 말하라.
            참조 문서는 시스템이 화면에 따로 표시한다 — 답변 안에 출처를 표기하지 마라:
            출처 목록, 파일명, 증권사·언론사명, [1] 같은 인용 번호, "리포트에 따르면" 같은
            표현 전부 금지. 답변에는 내용만 담아라. (사용자가 출처 자체를 물을 때만 예외.)
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

    private final DocumentRetrievalService retrievalService;

    protected AbstractRagChatService(ChatClient.Builder chatClientBuilder,
                                     ChatHistoryService chatHistoryService,
                                     DocumentRetrievalService retrievalService,
                                     String systemPrompt) {
        super(chatClientBuilder, chatHistoryService, systemPrompt);
        this.retrievalService = retrievalService;
    }

    @Override
    protected List<Message> augment(String sessionId, String userMessage, List<Message> messages,
                                    Consumer<ChatStreamEvent> eventEmitter) {
        List<Message> augmented = new ArrayList<>();
        augmented.add(new SystemMessage(buildRetrievalContext(sessionId, userMessage, eventEmitter)));
        augmented.addAll(messages);
        return augmented;
    }

    private String buildRetrievalContext(String sessionId, String userMessage,
                                         Consumer<ChatStreamEvent> events) {
        events.accept(ChatStreamEvent.status("문서 검색 중..."));
        DocumentRetrievalService.Retrieval retrieval;
        try {
            retrieval = retrievalService.retrieve(sessionId, userMessage);
        } catch (Exception e) {
            log.warn("문서 벡터 검색 실패 — 검색 없이 대화 계속 (session={}): {}", sessionId, e.getMessage());
            events.accept(ChatStreamEvent.status("문서 검색 실패 — 문서 참조 없이 답변합니다"));
            return SEARCH_FAILED_NOTICE;
        }

        List<Document> sessionResults = retrieval.sessionResults();
        List<Document> sharedResults = retrieval.sharedResults();
        if (retrieval.isEmpty()) {
            log.debug("문서 검색 결과 없음 (session={}, query={})", sessionId, userMessage);
            events.accept(ChatStreamEvent.status("문서 검색 완료 — 관련 발췌 없음"));
            return NO_MATCH_NOTICE;
        }

        events.accept(ChatStreamEvent.status(sessionResults.isEmpty()
                ? "문서 검색 완료 — 공용 문서 발췌 %d건 참조".formatted(sharedResults.size())
                : "문서 검색 완료 — 첨부 %d건 · 공용 %d건 참조".formatted(sessionResults.size(), sharedResults.size())));
        events.accept(ChatStreamEvent.sources(
                DocumentRetrievalService.distinctSourceNames(sessionResults, sharedResults)));

        log.debug("문서 검색 주입: 세션 첨부 {}건 + 공용 {}건 (session={})",
                sessionResults.size(), sharedResults.size(), sessionId);
        StringBuilder context = new StringBuilder(CONTEXT_HEADER);
        int index = 1;
        if (!sessionResults.isEmpty()) {
            context.append(SESSION_SECTION_HEADER);
            index = DocumentRetrievalService.appendExcerpts(context, sessionResults, index);
        }
        if (!sharedResults.isEmpty()) {
            context.append(SHARED_SECTION_HEADER);
            DocumentRetrievalService.appendExcerpts(context, sharedResults, index);
        }
        return context.toString();
    }
}
