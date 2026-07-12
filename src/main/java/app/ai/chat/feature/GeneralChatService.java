package app.ai.chat.feature;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Low-level tier: talks to {@link ChatModel} directly with hand-built {@link Prompt}/{@link
 * org.springframework.ai.chat.messages.Message}s instead of the {@link org.springframework.ai.chat.client.ChatClient}
 * fluent API used by the other feature services. See docs/spring-ai-chatmodel-vs-chatclient.md
 * for how the two tiers correspond.
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
        Prompt prompt = new Prompt(List.of(new SystemMessage(SYSTEM_PROMPT), new UserMessage(userMessage)));

        return chatModel.stream(prompt)
                .mapNotNull(response -> {
                    Generation result = response.getResult();
                    return result != null ? result.getOutput().getText() : null;
                });
    }
}
