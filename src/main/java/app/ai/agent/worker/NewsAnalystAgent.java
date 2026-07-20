package app.ai.agent.worker;

import app.ai.agent.AgentTeamProperties;
import app.ai.agent.SubAgent;
import app.ai.chat.tool.DateTimeTools;
import app.ai.chat.tool.NewsSearchTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 뉴스 분석가 (docs/13-multi-agent-design.md 3.1절) — 뉴스 저장소(ES, 읽기 전용) 전담.
 * 뉴스는 출처 UI 표시 대상이 아니므로(문서·웹만 — docs/08 3절 6항) SOURCES 발행이 없다.
 */
@Component
public class NewsAnalystAgent extends SubAgent {

    private static final String SYSTEM_PROMPT = """
            You are the news analyst of a Korean financial assistant team.
            You receive one sub-task and answer it from the news archive via searchNews
            (recent market/economic events, company or issue coverage). Use
            getCurrentDateTime whenever relative dates matter ("최근", "이번 주") —
            never guess today's date. Re-search with a better query if needed.
            Return a compact Korean digest of the facts — plain sentences only, no
            Markdown, no headers, no press names or URLs. Dates of events may be
            included when they matter.
            If nothing relevant is found, say exactly that — never fabricate news.
            """;

    public NewsAnalystAgent(ChatClient.Builder chatClientBuilder, AgentTeamProperties properties,
                            NewsSearchTools newsSearchTools, DateTimeTools dateTimeTools) {
        super(chatClientBuilder, properties, "뉴스 분석가", SYSTEM_PROMPT,
                newsSearchTools, dateTimeTools);
    }

    @Tool(description = """
            뉴스 분석가에게 하위 과제를 위임한다. 최근 뉴스, 시장·경제 이벤트, 특정 기업·
            이슈 관련 소식이 필요할 때 사용한다.""")
    public String askNewsAnalyst(
            @ToolParam(description = "하위 과제 — 찾을 뉴스 주제를 한국어 한 문장으로") String task,
            ToolContext toolContext) {
        return delegate(task, toolContext);
    }
}
