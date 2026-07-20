package app.ai.agent.worker;

import app.ai.agent.AgentTeamProperties;
import app.ai.agent.SubAgent;
import app.ai.chat.tool.DocumentSearchTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 문서 분석가 (docs/13-multi-agent-design.md 3.1절) — 공용 리포트 코퍼스와 이 세션에
 * 업로드된 문서 전담. 세션 격리는 부모 {@code ToolContext}의 sessionId로 기존과 동일하게
 * 성립하고, 검색 성공 시 SOURCES 이벤트(파일 칩)도 기존 경로로 발행된다.
 */
@Component
public class DocumentAnalystAgent extends SubAgent {

    private static final String SYSTEM_PROMPT = """
            You are the document analyst of a Korean financial assistant team.
            You receive one sub-task and answer it from analyst reports and uploaded
            files via searchDocuments (전망, 목표주가, 리포트 내용). Re-search with a
            better query if the first result is insufficient.
            Return a compact Korean digest of the facts — plain sentences only, no
            Markdown, no headers, no file names or source names.
            If nothing relevant is found, say exactly that — never fabricate content.
            """;

    public DocumentAnalystAgent(ChatClient.Builder chatClientBuilder, AgentTeamProperties properties,
                                DocumentSearchTools documentSearchTools) {
        super(chatClientBuilder, properties, "문서 분석가", SYSTEM_PROMPT, documentSearchTools);
    }

    @Tool(description = """
            문서 분석가에게 하위 과제를 위임한다. 증권사 리포트나 이 대화에 업로드된 문서의
            내용(전망, 목표주가, 분석 내용)이 필요할 때 사용한다.""")
    public String askDocumentAnalyst(
            @ToolParam(description = "하위 과제 — 문서에서 찾을 내용을 한국어 한 문장으로") String task,
            ToolContext toolContext) {
        return delegate(task, toolContext);
    }
}
