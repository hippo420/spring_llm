package app.ai.chat.history.memory;

import app.ai.chat.dto.ChatMessageDto;
import app.ai.chat.dto.ChatSessionSummary;
import app.ai.chat.dto.ConversationContext;
import app.ai.chat.history.ChatHistoryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * 계층형 히스토리 설계의 1단계 (docs/chat-history-tiered-storage-design.md):
 * 인메모리 전용이지만, 메시지에는 2단계(Redis)와 3단계(RDBMS 아카이브/요약)가 기반으로
 * 삼을 세션별 단조 증가 seq가 이미 부여된다. 여기서 요약은 항상 비어 있다.
 *
 * <p>{@code app.chat.history.mode=memory}일 때 활성화된다 (설정이 없으면 기본값).
 */
@Service
@ConditionalOnProperty(name = "app.chat.history.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryChatHistoryService implements ChatHistoryService {

    private record StoredMessage(long seq, String role, String content, Instant createdAt) {
    }

    private static final class Session {
        final String id;
        volatile String title; // 첫 턴 완료 후 LLM 요약 제목으로 교체될 수 있다
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
    public void updateSessionTitle(String sessionId, String title) {
        Session session = sessions.get(sessionId);
        if (session != null) {
            session.title = title;
        }
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

    // CopyOnWriteArrayList의 subList 뷰는 동시 쓰기 시 예외가 날 수 있다; 복사본은 안전하게 자를 수 있다.
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
