package app.ai.chat.history;

import app.ai.chat.dto.ChatMessageDto;
import app.ai.chat.dto.ConversationContext;
import app.ai.chat.history.repository.ChatMessageRepository;
import app.ai.chat.history.repository.ChatSessionRepository;
import app.ai.chat.history.repository.ChatSummaryRepository;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 계층형 구현 — Redis가 최근 대화(활성 구간), RDBMS가 전체 대화의 보관(원장)을 맡는다
 * (docs/chat-history-tiered-storage-design.md 2절의 L2+L3).
 *
 * <p><b>쓰기</b>: 먼저 JPA로 영속화해 seq를 발급받고(원장 — 유실되면 안 되는 쪽),
 * 같은 메시지를 Redis 리스트({@code chat:{sid}:messages})에 write-through로 추가한 뒤
 * 윈도우 크기만큼만 남기고 잘라낸다(LTRIM). 윈도우 밖으로 밀려난 오래된 메시지는
 * Redis에서 사라지지만 DB에는 전부 남는다 — "Redis=최근, DB=전체 보관".
 *
 * <p><b>읽기</b>: 매 턴 발생하는 프롬프트 조립({@link #getContext})의 최근 메시지는
 * Redis에서 읽어 DB 부하를 줄인다. TTL 만료 등으로 키가 비어 있으면 DB에서 읽고
 * Redis에 재수화(rehydrate)한다. 전체 히스토리(화면 표시)·세션 목록·요약은 오래된
 * 데이터까지 필요하므로 DB에서 읽는다 (상속한 JPA 경로 그대로).
 *
 * <p><b>Redis 장애</b>: 캐시 쓰기/읽기 실패는 경고 로그만 남기고 DB 경로로 폴백한다 —
 * 캐시 문제가 대화 흐름을 절대 막지 않는다 (설계 문서 7절).
 *
 * <p>{@code app.chat.history.mode=tiered}일 때 활성화된다.
 */
@Service
@ConditionalOnProperty(name = "app.chat.history.mode", havingValue = "tiered")
public class TieredChatHistoryService extends JpaChatHistoryService {

    private static final Logger log = LoggerFactory.getLogger(TieredChatHistoryService.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final int windowSize;
    private final Duration ttl;

    public TieredChatHistoryService(ChatSessionRepository sessionRepository,
                                    ChatMessageRepository messageRepository,
                                    ChatSummaryRepository summaryRepository,
                                    StringRedisTemplate redis,
                                    ObjectMapper objectMapper,
                                    @Value("${app.chat.history.window-size:20}") int windowSize,
                                    @Value("${app.chat.history.redis.ttl:7d}") Duration ttl) {
        super(sessionRepository, messageRepository, summaryRepository, windowSize);
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.windowSize = windowSize;
        this.ttl = ttl;
    }

    @Override
    @Transactional
    public long appendMessage(String sessionId, String role, String content) {
        // 1) DB 원장에 먼저 영속화 (seq 발급 포함) — 여기가 실패하면 예외로 중단된다.
        long seq = super.appendMessage(sessionId, role, content);
        // 2) Redis 최근 윈도우에 write-through — 실패해도 대화는 계속된다.
        cacheRecent(sessionId, new ChatMessageDto(role, content, Instant.now()));
        return seq;
    }

    @Override
    public ConversationContext getContext(String sessionId) {
        List<ChatMessageDto> recent = readRecentFromRedis(sessionId);
        if (recent == null) {
            // 캐시 미스(TTL 만료·신규 복원·Redis 장애) — DB에서 조립하고 재수화한다.
            ConversationContext fromDb = super.getContext(sessionId);
            rehydrate(sessionId, fromDb.recentMessages());
            return fromDb;
        }
        // 요약의 원본은 DB(chat_summary) — 최근 메시지만 Redis에서 왔다.
        return new ConversationContext(findLatestSummary(UUID.fromString(sessionId)), recent);
    }

    @Override
    @Transactional
    public void deleteSession(String sessionId) {
        super.deleteSession(sessionId);
        try {
            redis.delete(messagesKey(sessionId));
        } catch (RuntimeException e) {
            // TTL이 어차피 지워 준다 — DB 삭제가 성공했으므로 치명적이지 않다.
            log.warn("Redis 세션 키 삭제 실패 (session={}): {}", sessionId, e.getMessage());
        }
    }

    // createSession / findSessions / findMessages 는 JPA(DB) 경로를 그대로 상속한다 —
    // 세션 목록과 전체 히스토리는 오래된 세션까지 보여야 하므로 원장이 기준이다.

    /** 메시지를 Redis 리스트 끝에 추가하고 최근 윈도우만 남긴다. 활동 시 TTL 갱신. */
    private void cacheRecent(String sessionId, ChatMessageDto message) {
        try {
            String key = messagesKey(sessionId);
            redis.opsForList().rightPush(key, toJson(message));
            redis.opsForList().trim(key, -windowSize, -1);
            redis.expire(key, ttl);
        } catch (RuntimeException e) {
            log.warn("Redis 최근 대화 캐시 실패 — DB에는 저장됨 (session={}): {}", sessionId, e.getMessage());
        }
    }

    /** Redis에서 최근 윈도우를 읽는다. 키가 없거나 Redis 장애면 null(=캐시 미스). */
    private List<ChatMessageDto> readRecentFromRedis(String sessionId) {
        try {
            String key = messagesKey(sessionId);
            List<String> jsons = redis.opsForList().range(key, -windowSize, -1);
            if (jsons == null || jsons.isEmpty()) {
                return null;
            }
            redis.expire(key, ttl);
            return jsons.stream().map(this::fromJson).toList();
        } catch (RuntimeException e) {
            log.warn("Redis 최근 대화 조회 실패 — DB로 폴백 (session={}): {}", sessionId, e.getMessage());
            return null;
        }
    }

    /** DB에서 읽은 최근 윈도우를 Redis에 다시 채운다 (TTL 만료 세션 재개 경로, 5.3절). */
    private void rehydrate(String sessionId, List<ChatMessageDto> recentMessages) {
        if (recentMessages.isEmpty()) {
            return;
        }
        try {
            String key = messagesKey(sessionId);
            List<String> jsons = recentMessages.stream().map(this::toJson).toList();
            redis.delete(key);
            redis.opsForList().rightPushAll(key, jsons);
            redis.expire(key, ttl);
        } catch (RuntimeException e) {
            log.warn("Redis 재수화 실패 — 다음 조회도 DB로 폴백 (session={}): {}", sessionId, e.getMessage());
        }
    }

    private static String messagesKey(String sessionId) {
        return "chat:" + sessionId + ":messages";
    }

    // Jackson 3의 JacksonException은 언체크 예외라 별도 래핑이 필요 없다 —
    // 직렬화 실패는 호출부의 RuntimeException 폴백(캐시 실패 → DB)으로 흡수된다.
    private String toJson(ChatMessageDto message) {
        return objectMapper.writeValueAsString(message);
    }

    private ChatMessageDto fromJson(String json) {
        return objectMapper.readValue(json, ChatMessageDto.class);
    }
}
