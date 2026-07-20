package app.ai.agent.worker;

import app.ai.agent.AgentTeamProperties;
import app.ai.agent.SubAgent;
import app.ai.chat.tool.DateTimeTools;
import app.ai.chat.tool.FinanceDbTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 시장 데이터 분석가 (docs/13-multi-agent-design.md 3.1절) — 사내 DB의 수치 데이터
 * 전담. 도구 선택 규칙은 TOOL_RAG 프롬프트의 Financial DB 부분만 이관한 것이다.
 */
@Component
public class MarketDataAgent extends SubAgent {

    private static final String SYSTEM_PROMPT = """
            You are the market-data analyst of a Korean financial assistant team.
            You receive one sub-task and answer it with your read-only DB tools:
            searchStock (company lookup), getStockPrice (daily prices), getTopStocks
            (rankings), getInvestorTrend (investor flows), getFinancialStatement,
            getEconomicEvents (economic calendar). Use getCurrentDateTime whenever
            relative dates matter ("오늘", "이번 주") — never guess today's date.
            Return a compact Korean digest of the facts with concrete figures — plain
            sentences only, no Markdown, no headers, no as-of/기준일 remarks.
            If a tool fails or finds nothing, say exactly that — never fabricate figures.
            """;

    public MarketDataAgent(ChatClient.Builder chatClientBuilder, AgentTeamProperties properties,
                           FinanceDbTools financeDbTools, DateTimeTools dateTimeTools) {
        super(chatClientBuilder, properties, "시장 데이터 분석가", SYSTEM_PROMPT,
                financeDbTools, dateTimeTools);
    }

    @Tool(description = """
            시장 데이터 분석가에게 하위 과제를 위임한다. 주가·시가총액·순위·투자자 수급·
            재무제표·경제 캘린더 등 사내 DB의 수치 데이터가 필요할 때 사용한다.""")
    public String askMarketAnalyst(
            @ToolParam(description = "하위 과제 — 원하는 데이터와 대상(종목명 등)을 한국어 한 문장으로") String task,
            ToolContext toolContext) {
        return delegate(task, toolContext);
    }
}
