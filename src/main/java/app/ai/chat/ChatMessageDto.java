package app.ai.chat;

import java.time.Instant;

public record ChatMessageDto(String role, String content, Instant createdAt) {
}
