package app.ai.agent.critic;

import app.ai.agent.AgentTeamProperties;
import app.ai.agent.AgentTeamService;
import app.ai.agent.worker.DocumentAnalystAgent;
import app.ai.agent.worker.MarketDataAgent;
import app.ai.agent.worker.NewsAnalystAgent;
import app.ai.agent.worker.WebResearchAgent;
import app.ai.chat.history.ChatHistoryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * CRITIC_AGENT_TEAM 기능 구현 — {@link AgentTeamService}(멀티 에이전트)에 비평가 루프를
 * 붙인 변형. 위임·워커·타임라인 동작은 부모와 동일하고, 차이는 답변 마무리뿐이다:
 * 초안을 실시간 스트리밍하는 대신 버퍼링해 {@link CriticLoop} 검증(필요 시 1회 재작성)을
 * 거친 최종본만 내보낸다. AGENT_TEAM(부모 빈)은 비평 없이 기존 그대로 동작하므로,
 * 기능 드롭다운 선택이 곧 비평가 온오프 스위치다.
 */
@Service
public class CriticAgentTeamService extends AgentTeamService {

    public CriticAgentTeamService(ChatClient.Builder chatClientBuilder,
                                  ChatHistoryService chatHistoryService,
                                  AgentTeamProperties properties,
                                  MarketDataAgent marketDataAgent,
                                  DocumentAnalystAgent documentAnalystAgent,
                                  NewsAnalystAgent newsAnalystAgent,
                                  ObjectProvider<WebResearchAgent> webResearchAgentProvider,
                                  CriticLoop criticLoop) {
        super(chatClientBuilder, chatHistoryService, properties, marketDataAgent,
                documentAnalystAgent, newsAnalystAgent, webResearchAgentProvider, criticLoop);
    }
}
