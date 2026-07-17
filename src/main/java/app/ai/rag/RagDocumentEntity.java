package app.ai.rag;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * rag_document 테이블 (docs/06-doc-rag-design.md 4.2절) — 세션에 업로드된 문서의 관리 원장.
 * 청크 본문/임베딩은 pgvector(chat_doc_chunk)에 있고, 이 엔티티는 "이 세션에 어떤 문서가
 * 몇 청크로 들어갔나"를 벡터 테이블 조회 없이 답하기 위한 메타데이터만 가진다.
 */
@Entity
@Table(name = "rag_document")
public class RagDocumentEntity {

    public enum Status {
        /** 인덱싱 완료 — 검색 대상. */
        READY,
        /** 파싱/임베딩 실패 — 청크가 없으므로 검색에 영향 없음. 원인 추적용으로 남긴다. */
        FAILED
    }

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RagDocumentEntity() {
        // JPA 전용
    }

    public RagDocumentEntity(UUID id, UUID sessionId, String filename, String contentType,
                             long sizeBytes, int chunkCount, Status status) {
        this.id = id;
        this.sessionId = sessionId;
        this.filename = filename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.chunkCount = chunkCount;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public UUID id() {
        return id;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public String filename() {
        return filename;
    }

    public String contentType() {
        return contentType;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public int chunkCount() {
        return chunkCount;
    }

    public Status status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
