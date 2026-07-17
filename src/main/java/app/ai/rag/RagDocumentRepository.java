package app.ai.rag;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RagDocumentRepository extends JpaRepository<RagDocumentEntity, UUID> {

    /** 세션의 문서 목록, 업로드 순. */
    List<RagDocumentEntity> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    /** DOC_QA 검색 전 "이 세션에 검색할 문서가 있나" 판별용. */
    boolean existsBySessionIdAndStatus(UUID sessionId, RagDocumentEntity.Status status);

    void deleteBySessionId(UUID sessionId);
}
