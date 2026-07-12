package app.ai.chat;

import java.time.Instant;

public record ChatSessionSummary(String id, String title, Instant createdAt) {
}
