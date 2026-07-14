package app.ai.chat.history.entity;

import app.ai.chat.history.repository.ChatSessionRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * chat_session 테이블 (설계 문서 3.3절). 세션 메타데이터와 함께, 세션별 단조 증가
 * seq의 발급 기준인 {@code lastSeq}를 보관한다 — seq 발급은 반드시
 * {@link #nextSeq()}를 통해서만 하고, 호출 전에 세션 로우에 쓰기 잠금을 잡아야 한다
 * ({@link ChatSessionRepository#findByIdForUpdate}).
 */
@Entity
@Table(name = "chat_session")
public class ChatSessionEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 이 세션에서 마지막으로 발급된 seq. 0이면 아직 메시지가 없다. */
    @Column(name = "last_seq", nullable = false)
    private long lastSeq;

    protected ChatSessionEntity() {
        // JPA 전용
    }

    public ChatSessionEntity(UUID id, String title, Instant createdAt) {
        this.id = id;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.lastSeq = 0;
    }

    /** 세션 제목을 바꾼다 (예: 첫 턴 완료 후 LLM이 요약해 만든 제목). */
    public void rename(String title) {
        this.title = title;
        this.updatedAt = Instant.now();
    }

    /** 다음 seq를 발급하고 세션의 최근 활동 시각을 갱신한다. */
    public long nextSeq() {
        this.updatedAt = Instant.now();
        return ++this.lastSeq;
    }

    public UUID id() {
        return id;
    }

    public String title() {
        return title;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long lastSeq() {
        return lastSeq;
    }
}
