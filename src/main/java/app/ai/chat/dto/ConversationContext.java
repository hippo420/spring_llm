package app.ai.chat.dto;

import java.util.List;
import java.util.Optional;

/**
 * 기능 서비스가 프롬프트를 조립하는 데 필요한 것: 윈도우 밖으로 밀려난 내용 전체의 롤링
 * 요약(3단계 전까지는 비어 있음)과 윈도우 안의 최근 메시지들(오래된 순).
 */
public record ConversationContext(Optional<String> summary, List<ChatMessageDto> recentMessages) {
}
