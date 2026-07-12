package app.ai.chat;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory chat history store.
 * TODO: replace with a JPA-backed repository (ChatSession/ChatMessage entities) for durable persistence.
 */
@Component
public class ChatSessionStore {

    private record Session(String id, String title, Instant createdAt, List<ChatMessageDto> messages) {
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public List<ChatSessionSummary> findAll() {
        return sessions.values().stream()
                .sorted(Comparator.comparing(Session::createdAt).reversed())
                .map(s -> new ChatSessionSummary(s.id(), s.title(), s.createdAt()))
                .toList();
    }

    public ChatSessionSummary create() {
        String id = UUID.randomUUID().toString();
        Session session = new Session(id, "새 대화", Instant.now(), new CopyOnWriteArrayList<>());
        sessions.put(id, session);
        return new ChatSessionSummary(session.id(), session.title(), session.createdAt());
    }

    public void delete(String sessionId) {
        sessions.remove(sessionId);
    }

    public List<ChatMessageDto> findMessages(String sessionId) {
        Session session = sessions.get(sessionId);
        return session == null ? List.of() : session.messages();
    }

    public void appendUserMessage(String sessionId, String content) {
        append(sessionId, "user", content);
    }

    public void appendAssistantMessage(String sessionId, String content) {
        append(sessionId, "assistant", content);
    }

    private void append(String sessionId, String role, String content) {
        Session session = sessions.computeIfAbsent(sessionId,
                id -> new Session(id, "새 대화", Instant.now(), new CopyOnWriteArrayList<>()));
        session.messages().add(new ChatMessageDto(role, content, Instant.now()));
    }
}
