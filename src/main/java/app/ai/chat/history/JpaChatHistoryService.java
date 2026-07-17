package app.ai.chat.history;

import app.ai.chat.dto.ChatMessageDto;
import app.ai.chat.dto.ChatSessionSummary;
import app.ai.chat.dto.ConversationContext;
import app.ai.chat.history.entity.ChatMessageEntity;
import app.ai.chat.history.entity.ChatSessionEntity;
import app.ai.chat.history.entity.ChatSummaryEntity;
import app.ai.chat.history.repository.ChatMessageRepository;
import app.ai.chat.history.repository.ChatSessionRepository;
import app.ai.chat.history.repository.ChatSummaryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * RDBMS(PostgreSQL) 기반 구현 — JPA Entity({@link ChatSessionEntity},
 * {@link ChatMessageEntity}, {@link ChatSummaryEntity})로 대화를 영속화한다.
 * 설계 문서(docs/04-chat-history-tiered-storage-design.md)의 L3 스키마를 그대로 따르되,
 * 여기서는 백업이 아니라 단독 저장소로 동작한다. tiered 모드에서는
 * {@link TieredChatHistoryService}가 이 클래스를 보관 계층(L3)으로 상속 재사용한다.
 *
 * <p>seq 발급: 세션 로우를 쓰기 잠금으로 잡은 뒤 {@code last_seq}를 증가시킨다 —
 * 단일 DB가 조정자 역할을 하므로 인스턴스가 여러 개여도 중복이 없다.
 *
 * <p>{@code app.chat.history.mode=jpa}일 때 활성화된다.
 */
@Service
@ConditionalOnProperty(name = "app.chat.history.mode", havingValue = "jpa")
public class JpaChatHistoryService implements ChatHistoryService {

    private static final String DEFAULT_TITLE = "새 대화";

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatSummaryRepository summaryRepository;
    private final int windowSize;

    public JpaChatHistoryService(ChatSessionRepository sessionRepository,
                                 ChatMessageRepository messageRepository,
                                 ChatSummaryRepository summaryRepository,
                                 @Value("${app.chat.history.window-size:20}") int windowSize) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.summaryRepository = summaryRepository;
        this.windowSize = windowSize;
    }

    @Override
    @Transactional
    public ChatSessionSummary createSession() {
        ChatSessionEntity session =
                new ChatSessionEntity(UUID.randomUUID(), DEFAULT_TITLE, Instant.now());
        sessionRepository.save(session);
        return toSummary(session);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatSessionSummary> findSessions() {
        return sessionRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(JpaChatHistoryService::toSummary)
                .toList();
    }

    @Override
    @Transactional
    public void deleteSession(String sessionId) {
        UUID id = UUID.fromString(sessionId);
        summaryRepository.deleteBySessionId(id);
        messageRepository.deleteBySessionId(id);
        sessionRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void updateSessionTitle(String sessionId, String title) {
        // 더티 체킹으로 커밋 시 UPDATE된다. 세션이 이미 지워졌으면 조용히 무시.
        sessionRepository.findById(UUID.fromString(sessionId))
                .ifPresent(session -> session.rename(title));
    }

    @Override
    @Transactional
    public long appendMessage(String sessionId, String role, String content) {
        UUID id = UUID.fromString(sessionId);
        // 쓰기 잠금으로 세션을 잡아 seq 발급의 원자성을 보장한다. 세션 로우가 없으면
        // 만들어 준다 (인메모리 구현의 computeIfAbsent와 같은 관용).
        ChatSessionEntity session = sessionRepository.findByIdForUpdate(id)
                .orElseGet(() -> sessionRepository.save(
                        new ChatSessionEntity(id, DEFAULT_TITLE, Instant.now())));
        long seq = session.nextSeq();
        messageRepository.save(new ChatMessageEntity(id, seq, role, content, Instant.now()));
        return seq;
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationContext getContext(String sessionId) {
        UUID id = UUID.fromString(sessionId);

        Optional<String> summary = findLatestSummary(id);

        // 최신 순으로 윈도우만큼 가져온 뒤 오래된 순으로 뒤집는다.
        List<ChatMessageEntity> latestFirst =
                messageRepository.findBySessionIdOrderBySeqDesc(id, PageRequest.of(0, windowSize));
        List<ChatMessageDto> recent = new ArrayList<>(latestFirst.size());
        for (int i = latestFirst.size() - 1; i >= 0; i--) {
            recent.add(toDto(latestFirst.get(i)));
        }

        return new ConversationContext(summary, List.copyOf(recent));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageDto> findMessages(String sessionId) {
        return messageRepository.findBySessionIdOrderBySeqAsc(UUID.fromString(sessionId)).stream()
                .map(JpaChatHistoryService::toDto)
                .toList();
    }

    /** 최신 롤링 요약. 하위 계층 구현({@link TieredChatHistoryService})이 재사용한다. */
    protected Optional<String> findLatestSummary(UUID sessionId) {
        return summaryRepository.findTopBySessionIdOrderByUpToSeqDesc(sessionId)
                .map(ChatSummaryEntity::summary);
    }

    private static ChatSessionSummary toSummary(ChatSessionEntity session) {
        return new ChatSessionSummary(session.id().toString(), session.title(), session.createdAt());
    }

    private static ChatMessageDto toDto(ChatMessageEntity message) {
        return new ChatMessageDto(message.role(), message.content(), message.createdAt());
    }
}
