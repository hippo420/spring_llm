package app.ai.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stage 1 of the tiered history design (docs/chat-history-tiered-storage-design.md):
 * in-memory only, but messages already carry the per-session monotonic seq that Stage 2
 * (Redis) and Stage 3 (RDBMS archive/summaries) build on. Summaries are always empty here.
 */
@Service
public class InMemoryChatHistoryService implements ChatHistoryService {

    private record StoredMessage(long seq, String role, String content, Instant createdAt) {
    }

    private static final class Session {
        final String id;
        final String title;
        final Instant createdAt;
        final AtomicLong seq = new AtomicLong();
        final List<StoredMessage> messages = new CopyOnWriteArrayList<>();

        Session(String id, String title, Instant createdAt) {
            this.id = id;
            this.title = title;
            this.createdAt = createdAt;
        }
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final int windowSize;

    public InMemoryChatHistoryService(@Value("${app.chat.history.window-size:20}") int windowSize) {
        this.windowSize = windowSize;
    }

    @Override
    public ChatSessionSummary createSession() {
        Session session = new Session(UUID.randomUUID().toString(), "새 대화", Instant.now());
        sessions.put(session.id, session);
        return toSummary(session);
    }

    @Override
    public List<ChatSessionSummary> findSessions() {
        return sessions.values().stream()
                .sorted(Comparator.comparing((Session s) -> s.createdAt).reversed())
                .map(InMemoryChatHistoryService::toSummary)
                .toList();
    }

    @Override
    public void deleteSession(String sessionId) {
        sessions.remove(sessionId);
    }

    @Override
    public long appendMessage(String sessionId, String role, String content) {
        Session session = sessions.computeIfAbsent(sessionId,
                id -> new Session(id, "새 대화", Instant.now()));
        long seq = session.seq.incrementAndGet();
        session.messages.add(new StoredMessage(seq, role, content, Instant.now()));
        return seq;
    }

    @Override
    public ConversationContext getContext(String sessionId) {
        List<StoredMessage> snapshot = snapshotOf(sessionId);
        List<ChatMessageDto> recent = snapshot.subList(Math.max(0, snapshot.size() - windowSize), snapshot.size())
                .stream()
                .map(InMemoryChatHistoryService::toDto)
                .toList();
        return new ConversationContext(Optional.empty(), recent);
    }

    @Override
    public List<ChatMessageDto> findMessages(String sessionId) {
        return snapshotOf(sessionId).stream()
                .map(InMemoryChatHistoryService::toDto)
                .toList();
    }

    // COW subList views can throw on concurrent writes; a copy is safe to slice.
    private List<StoredMessage> snapshotOf(String sessionId) {
        Session session = sessions.get(sessionId);
        return session == null ? List.of() : List.copyOf(session.messages);
    }

    private static ChatSessionSummary toSummary(Session session) {
        return new ChatSessionSummary(session.id, session.title, session.createdAt);
    }

    private static ChatMessageDto toDto(StoredMessage message) {
        return new ChatMessageDto(message.role(), message.content(), message.createdAt());
    }
}
