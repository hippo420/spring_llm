package app.ai.chat.feature;

import app.ai.chat.ChatHistoryService;
import app.ai.chat.ChatMessageDto;
import app.ai.chat.ConversationContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * Advanced tier: built on the {@link ChatClient} fluent API (prototype-scoped {@link
 * ChatClient.Builder} per service, so each feature keeps its own default system prompt). See
 * {@link GeneralChatService} for the low-level {@code ChatModel}/{@code Prompt}/{@code Message}
 * tier, and docs/spring-ai-chatmodel-vs-chatclient.md for how the two correspond.
 *
 * <p>Multi-turn memory: the whole conversation window (summary + recent messages, ending with
 * the current user message — see the {@link ChatFeatureService#stream} contract) is passed via
 * {@code messages()}; the default system prompt is prepended by the client.
 */
abstract class AbstractChatFeatureService implements ChatFeatureService {

    private final ChatClient chatClient;
    private final ChatHistoryService chatHistoryService;

    protected AbstractChatFeatureService(ChatClient.Builder chatClientBuilder,
                                         ChatHistoryService chatHistoryService,
                                         String systemPrompt) {
        this.chatClient = chatClientBuilder.defaultSystem(systemPrompt).build();
        this.chatHistoryService = chatHistoryService;
    }

    @Override
    public Flux<String> stream(String sessionId, String userMessage) {
        ConversationContext context = chatHistoryService.getContext(sessionId);
        return chatClient.prompt()
                .messages(toMessages(context))
                .stream()
                .content();
    }

    private static List<Message> toMessages(ConversationContext context) {
        List<Message> messages = new ArrayList<>();
        context.summary().ifPresent(s ->
                messages.add(new SystemMessage("Summary of the earlier conversation:\n" + s)));
        for (ChatMessageDto dto : context.recentMessages()) {
            messages.add("user".equals(dto.role())
                    ? new UserMessage(dto.content())
                    : new AssistantMessage(dto.content()));
        }
        return messages;
    }
}
