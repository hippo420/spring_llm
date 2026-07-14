package app.ai.chat.dto;

import java.time.Instant;

public record ChatSessionSummary(String id, String title, Instant createdAt) {
}
