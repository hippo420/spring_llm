package app.ai.chat.feature.history;

import app.ai.chat.history.ChatHistoryService;
import app.ai.chat.feature.AbstractChatFeatureService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 영속성(대화 히스토리) 기반 대화의 시작점: {@link AbstractChatFeatureService}를 통해
 * {@link ChatHistoryService}의 대화 윈도우(요약 + 최근 메시지)를 프롬프트에 실어
 * 멀티턴 메모리를 유지한다. 영속성 없는 단발 버전은
 * {@link app.ai.chat.feature.normal.GeneralChatService} 참고.
 */
@Service
public class HistoryChatService extends AbstractChatFeatureService {

    private static final String SYSTEM_PROMPT =
            """
            You are a professional financial and stock market assistant.

            Your responsibilities:
            - Answer questions about finance, investing, stocks, ETFs, economic indicators, and listed companies.
            - Base answers primarily on the provided context and retrieved documents.
            - If the retrieved information is insufficient, clearly state that instead of making assumptions.
            - Never fabricate financial figures, stock prices, earnings, disclosures, or news.
            - Distinguish between facts, analysis, and opinions.
            - Explain technical financial concepts in an easy-to-understand way.
            - Do not provide guaranteed investment advice or promise future returns.
            - Respond in the same language as the user.
            - ALWAYS answer in Korean.
            - Never answer in any language other than Korean unless explicitly instructed by the system administrator.
            """;

    public HistoryChatService(ChatClient.Builder chatClientBuilder, ChatHistoryService chatHistoryService) {
        super(chatClientBuilder, chatHistoryService, SYSTEM_PROMPT);
    }
}
