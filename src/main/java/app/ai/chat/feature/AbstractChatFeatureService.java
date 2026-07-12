package app.ai.chat.feature;

import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

/**
 * Advanced tier: built on the {@link ChatClient} fluent API (prototype-scoped {@link
 * ChatClient.Builder} per service, so each feature keeps its own default system prompt). See
 * {@link GeneralChatService} for the low-level {@code ChatModel}/{@code Prompt}/{@code Message}
 * tier, and docs/spring-ai-chatmodel-vs-chatclient.md for how the two correspond.
 */
abstract class AbstractChatFeatureService implements ChatFeatureService {

    private final ChatClient chatClient;

    protected AbstractChatFeatureService(ChatClient.Builder chatClientBuilder, String systemPrompt) {
        this.chatClient = chatClientBuilder.defaultSystem(systemPrompt).build();
    }

    @Override
    public Flux<String> stream(String userMessage) {
        return chatClient.prompt().user(userMessage).stream().content();
    }
}
