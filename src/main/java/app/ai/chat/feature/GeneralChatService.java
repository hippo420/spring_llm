package app.ai.chat.feature;

import app.ai.chat.ChatHistoryService;
import app.ai.chat.ChatMessageDto;
import app.ai.chat.ConversationContext;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * Low-level tier: talks to {@link ChatModel} directly with hand-built {@link Prompt}/{@link
 * org.springframework.ai.chat.messages.Message}s instead of the {@link org.springframework.ai.chat.client.ChatClient}
 * fluent API used by the other feature services. See docs/spring-ai-chatmodel-vs-chatclient.md
 * for how the two tiers correspond. Multi-turn memory is likewise assembled by hand here —
 * this is exactly what the advanced tier's {@code messages()} call does behind the scenes.
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
    private final ChatHistoryService chatHistoryService;

    public GeneralChatService(ChatModel chatModel, ChatHistoryService chatHistoryService) {
        this.chatModel = chatModel;
        this.chatHistoryService = chatHistoryService;
    }

    @Override
    public Flux<String> stream(String sessionId, String userMessage) {
        ConversationContext context = chatHistoryService.getContext(sessionId);

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        context.summary().ifPresent(s ->
                messages.add(new SystemMessage("Summary of the earlier conversation:\n" + s)));
        for (ChatMessageDto dto : context.recentMessages()) {
            messages.add("user".equals(dto.role())
                    ? new UserMessage(dto.content())
                    : new AssistantMessage(dto.content()));
        }

        return chatModel.stream(new Prompt(messages))
                .mapNotNull(response -> {
                    Generation result = response.getResult();
                    return result != null ? result.getOutput().getText() : null;
                });
    }
}
