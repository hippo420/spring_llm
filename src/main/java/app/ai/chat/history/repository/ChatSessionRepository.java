package app.ai.chat.history.repository;

import app.ai.chat.history.entity.ChatSessionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, UUID> {

    List<ChatSessionEntity> findAllByOrderByCreatedAtDesc();

    /**
     * 세션 로우에 쓰기 잠금(SELECT ... FOR UPDATE)을 잡고 조회한다. seq 발급
     * ({@link ChatSessionEntity#nextSeq()}) 시 동시 요청 간 중복을 막는 장치.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ChatSessionEntity s where s.id = :id")
    Optional<ChatSessionEntity> findByIdForUpdate(@Param("id") UUID id);
}
