package app.ai.chat.history.repository;

import app.ai.chat.history.entity.ChatSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChatSummaryRepository extends JpaRepository<ChatSummaryEntity, Long> {

    /** 최신 롤링 요약 = up_to_seq가 가장 큰 로우. */
    Optional<ChatSummaryEntity> findTopBySessionIdOrderByUpToSeqDesc(UUID sessionId);

    void deleteBySessionId(UUID sessionId);
}
