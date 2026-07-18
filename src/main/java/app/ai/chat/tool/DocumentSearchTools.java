package app.ai.chat.tool;

import app.ai.chat.dto.ChatStreamEvent;
import app.ai.rag.DocumentRetrievalService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * 문서 벡터 검색의 도구화 (docs/09-tool-calling-design.md의 Non-goal을 TOOL_RAG에서 해제):
 * 자동 주입된 발췌(현재 질문 기준 1회 검색)로 부족할 때, 모델이 스스로 검색어를 바꿔
 * 추가 검색할 수 있다. 세션 스코프 격리는 {@link DocumentRetrievalService}가 동일하게
 * 보장한다 — sessionId는 모델이 아니라 {@link ToolContext}(서버가 요청마다 주입)에서 온다.
 */
@Component
public class DocumentSearchTools {

    /** ToolContext 키 — {@code ToolRagService}가 요청마다 현재 세션 ID를 넣는다. */
    public static final String SESSION_ID_KEY = "sessionId";

    private final DocumentRetrievalService retrievalService;

    public DocumentSearchTools(DocumentRetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @Tool(description = """
            공용 문서 저장소(증권사 리포트 등)와 이 대화에 업로드된 문서에서 검색어와 유사한
            내용을 찾아 발췌를 반환한다. 이미 제공된 발췌로 답할 수 없거나, 질문과 다른
            주제·종목의 문서 내용이 필요할 때 검색어를 바꿔 사용한다.""")
    public String searchDocuments(
            @ToolParam(description = "검색어 — 찾으려는 내용을 한국어로 구체적으로") String query,
            ToolContext toolContext) {
        try {
            String sessionId = (String) toolContext.getContext().get(SESSION_ID_KEY);
            DocumentRetrievalService.Retrieval retrieval = retrievalService.retrieve(sessionId, query);
            if (retrieval.isEmpty()) {
                return "검색 결과 없음 — 이 검색어와 관련된 문서를 찾지 못했다.";
            }
            emitSources(toolContext, retrieval);
            StringBuilder out = new StringBuilder(
                    "검색 결과 발췌 (파일명은 구분용 — 답변에 파일명·출처를 표기하지 말 것):\n");
            int index = DocumentRetrievalService.appendExcerpts(out, retrieval.sessionResults(), 1);
            DocumentRetrievalService.appendExcerpts(out, retrieval.sharedResults(), index);
            return out.toString();
        } catch (Exception e) {
            // 설계 09 7절: 도구 예외는 던지지 않고 실패를 결과로 반환 — 모델이 실패를
            // 인지하고 자연어로 안내하게 한다.
            return "검색 실패: " + e.getMessage();
        }
    }

    /**
     * 검색된 문서를 SOURCES 이벤트로 발행 — 자동 주입 없이 도구로만 검색하는
     * {@code ToolRagService}에서도 화면의 "참조 문서" 표시가 유지된다. 프런트는 여러 번의
     * sources 이벤트를 병합해 누적한다. 발행 콜백이 없는 컨텍스트면 조용히 건너뛴다.
     */
    @SuppressWarnings("unchecked")
    private static void emitSources(ToolContext toolContext, DocumentRetrievalService.Retrieval retrieval) {
        Object emitter = toolContext.getContext().get(StatusEmittingToolCallback.EMITTER_KEY);
        if (emitter instanceof Consumer) {
            String names = DocumentRetrievalService.distinctSourceNames(
                    retrieval.sessionResults(), retrieval.sharedResults());
            if (!names.isEmpty()) {
                ((Consumer<ChatStreamEvent>) emitter).accept(ChatStreamEvent.sources(names));
            }
        }
    }
}
