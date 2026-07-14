package app.ai.chat.history.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * chat_summary 테이블 (설계 문서 3.3절). LLM 롤링 요약을 {@code upToSeq}별로 누적한다 —
 * 최신 요약은 {@code ORDER BY up_to_seq DESC LIMIT 1}. 요약 생성 파이프라인(6절)은
 * 아직 미구현이므로 지금은 읽기 경로만 사용한다.
 */
@Entity
@Table(name = "chat_summary",
        uniqueConstraints = @UniqueConstraint(name = "uk_chat_summary_session_seq", columnNames = {"session_id", "up_to_seq"}))
public class ChatSummaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    /** 이 요약이 커버하는 마지막 메시지의 seq. */
    @Column(name = "up_to_seq", nullable = false)
    private long upToSeq;

    /** 요약을 생성한 모델 (재생성 판단용). */
    @Column(length = 60)
    private String model;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ChatSummaryEntity() {
        // JPA 전용
    }

    public ChatSummaryEntity(UUID sessionId, String summary, long upToSeq, String model, Instant createdAt) {
        this.sessionId = sessionId;
        this.summary = summary;
        this.upToSeq = upToSeq;
        this.model = model;
        this.createdAt = createdAt;
    }

    public Long id() {
        return id;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public String summary() {
        return summary;
    }

    public long upToSeq() {
        return upToSeq;
    }

    public String model() {
        return model;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
