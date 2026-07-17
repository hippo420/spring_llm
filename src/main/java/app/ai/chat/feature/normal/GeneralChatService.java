package app.ai.chat.feature.normal;

import app.ai.chat.feature.ChatFeatureService;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 영속성 없는 저수준 계층: 대화 히스토리를 전혀 사용하지 않고, 시스템 프롬프트 +
 * 현재 사용자 메시지 하나로만 응답하는 단발 대화. 다른 기능 서비스들이 쓰는
 * {@link org.springframework.ai.chat.client.ChatClient} 플루언트 API 대신, 직접 만든
 * {@link Prompt}/{@link Message}로 {@link ChatModel}과 직접 통신한다.
 * 두 계층의 대응 관계는 docs/01-spring-ai-chatmodel-vs-chatclient.md 참고.
 * 영속성(멀티턴 메모리)이 붙는 버전은 {@link app.ai.chat.feature.history.HistoryChatService} 참고.
 */
@Service
public class GeneralChatService implements ChatFeatureService {

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

    private final ChatModel chatModel;

    public GeneralChatService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public Flux<String> stream(String userMessage) {
        List<Message> messages = List.of(
                new SystemMessage(SYSTEM_PROMPT),
                new UserMessage(userMessage));

        return chatModel.stream(new Prompt(messages))
                .mapNotNull(response -> {
                    Generation result = response.getResult();
                    return result != null ? result.getOutput().getText() : null;
                });
    }
}
