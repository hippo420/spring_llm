package app.ai.chat;

import java.util.List;

/**
 * Single entry point for conversation storage. Callers (controller, feature services) never
 * see the storage tiers behind it — Stage 1 is in-memory, Stage 2 adds Redis, Stage 3 adds
 * RDBMS archive + rolling summaries. See docs/chat-history-tiered-storage-design.md.
 */
public interface ChatHistoryService {

    ChatSessionSummary createSession();

    List<ChatSessionSummary> findSessions();

    /** Removes the session from every tier (messages, and later: archive + summaries). */
    void deleteSession(String sessionId);

    /**
     * Appends a message and returns its per-session monotonic sequence number. The seq is
     * what keeps tiers consistent in later stages (idempotent archiving, restore points).
     */
    long appendMessage(String sessionId, String role, String content);

    /**
     * Prompt-assembly view: latest rolling summary (empty until Stage 3) plus the most
     * recent window of messages. Window size is configuration
     * ({@code app.chat.history.window-size}), not a caller decision.
     */
    ConversationContext getContext(String sessionId);

    /** Full history for display (UI session reload), oldest first. */
    List<ChatMessageDto> findMessages(String sessionId);
}
