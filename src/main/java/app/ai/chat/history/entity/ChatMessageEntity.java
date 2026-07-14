package app.ai.chat.history.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * chat_message 테이블 (설계 문서 3.3절). {@code UNIQUE(session_id, seq)}가 멱등 백업의
 * 핵심 — 같은 메시지를 두 번 저장하려는 시도는 제약 위반으로 걸러진다.
 */
@Entity
@Table(name = "chat_message",
        uniqueConstraints = @UniqueConstraint(name = "uk_chat_message_session_seq", columnNames = {"session_id", "seq"}),
        indexes = @Index(name = "idx_chat_message_session", columnList = "session_id, seq"))
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(nullable = false)
    private long seq;

    /** 'user' | 'assistant' */
    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ChatMessageEntity() {
        // JPA 전용
    }

    public ChatMessageEntity(UUID sessionId, long seq, String role, String content, Instant createdAt) {
        this.sessionId = sessionId;
        this.seq = seq;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }

    public Long id() {
        return id;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public long seq() {
        return seq;
    }

    public String role() {
        return role;
    }

    public String content() {
        return content;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
