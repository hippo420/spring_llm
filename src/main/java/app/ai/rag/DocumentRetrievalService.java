package app.ai.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * 문서 벡터 검색 (docs/06-doc-rag-design.md 6.2절) — 두 스코프를 함께 검색한다.
 *
 * <ul>
 *   <li><b>공용 코퍼스</b> — chat_doc_chunk에 외부 파이프라인이 적재해 둔 문서
 *       (증권사 리포트 등, {@code sessionId} 메타데이터가 없다). 모든 세션이 참조한다.</li>
 *   <li><b>세션 첨부</b> — 세션에 업로드된 문서({@link DocumentIngestionService}가
 *       {@code sessionId}를 붙여 인덱싱). 업로드한 세션에서만 검색되고, 공용 검색
 *       결과에서는 후처리 필터로 배제되므로 다른 세션/사용자에게 절대 노출되지 않는다.</li>
 * </ul>
 *
 * <p>검색 실패는 예외로 전파한다 — "실패해도 대화는 계속" 같은 폴백 정책은 호출자
 * (프롬프트 증강, 검색 도구)마다 다르므로 여기서 정하지 않는다.
 */
@Service
public class DocumentRetrievalService {

    public record Retrieval(List<Document> sessionResults, List<Document> sharedResults) {
        public boolean isEmpty() {
            return sessionResults.isEmpty() && sharedResults.isEmpty();
        }
    }

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

    public DocumentRetrievalService(VectorStore vectorStore,
                                    RagDocumentRepository ragDocumentRepository,
                                    @Value("${app.rag.top-k:4}") int topK,
                                    @Value("${app.rag.similarity-threshold:0.30}") double similarityThreshold) {
        this.vectorStore = vectorStore;
        this.ragDocumentRepository = ragDocumentRepository;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
    }

    /** 이중 스코프 검색. {@code sessionId}가 null이면 공용 코퍼스만 검색한다. */
    public Retrieval retrieve(String sessionId, String query) {
        return new Retrieval(searchSessionAttachments(sessionId, query), searchSharedCorpus(query));
    }

    /** 세션 첨부 검색 — 항상 {@code sessionId} 필터. 다른 세션의 첨부는 절대 걸리지 않는다. */
    private List<Document> searchSessionAttachments(String sessionId, String query) {
        if (sessionId == null || !ragDocumentRepository.existsBySessionIdAndStatus(
                UUID.fromString(sessionId), RagDocumentEntity.Status.READY)) {
            return List.of();
        }
        List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
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
     * 자바에서 세션 청크를 제외한다. 어떤 세션의 첨부든(현재 세션 포함 — 첨부는
     * {@link #searchSessionAttachments}가 담당) 이 필터에서 탈락하므로, 한 사용자의
     * 첨부가 다른 사용자의 프롬프트에 섞이는 일은 구조적으로 불가능하다.
     */
    private List<Document> searchSharedCorpus(String query) {
        List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
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

    /** 발췌 블록 조립: {@code [n] 파일명 \n 본문}. 다음 인덱스를 반환한다. */
    public static int appendExcerpts(StringBuilder out, List<Document> docs, int startIndex) {
        int i = startIndex;
        for (Document doc : docs) {
            out.append('[').append(i++).append("] ")
                    .append(sourceName(doc))
                    .append('\n')
                    .append(doc.getText())
                    .append('\n');
        }
        return i;
    }

    public static String sourceName(Document doc) {
        Object name = doc.getMetadata().get(DocumentIngestionService.META_FILENAME);
        if (name == null) {
            name = doc.getMetadata().get(META_SHARED_SOURCE);
        }
        return name == null ? "unknown" : name.toString();
    }

    /** 참조 문서 파일명 목록 (세션 첨부 우선, 중복 제거, 개행 구분) — SOURCES 이벤트 payload. */
    public static String distinctSourceNames(List<Document> sessionResults, List<Document> sharedResults) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Document doc : sessionResults) {
            names.add(sourceName(doc));
        }
        for (Document doc : sharedResults) {
            names.add(sourceName(doc));
        }
        names.remove("unknown");   // 이름 없는 청크는 표시 가치가 없다
        return String.join("\n", names);
    }
}
