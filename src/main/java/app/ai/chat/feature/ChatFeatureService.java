package app.ai.chat.feature;

import reactor.core.publisher.Flux;

public interface ChatFeatureService {

    /**
     * Streams the assistant reply. Contract: the caller has already appended the current
     * user message to {@link app.ai.chat.ChatHistoryService}, so the session context ends
     * with {@code userMessage}.
     */
    Flux<String> stream(String sessionId, String userMessage);
}
