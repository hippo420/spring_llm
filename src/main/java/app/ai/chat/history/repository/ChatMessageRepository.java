package app.ai.chat.history.repository;

import app.ai.chat.history.entity.ChatMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

    /** 화면 표시용 전체 히스토리, 오래된 순. */
    List<ChatMessageEntity> findBySessionIdOrderBySeqAsc(UUID sessionId);

    /** 프롬프트 조립용 최근 윈도우 — Pageable로 개수를 제한하고 최신 순으로 가져온다. */
    List<ChatMessageEntity> findBySessionIdOrderBySeqDesc(UUID sessionId, Pageable pageable);

    void deleteBySessionId(UUID sessionId);
}
