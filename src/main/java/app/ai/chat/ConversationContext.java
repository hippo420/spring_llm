package app.ai.chat;

import java.util.List;
import java.util.Optional;

/**
 * What a feature service needs to assemble a prompt: the rolling summary of everything that
 * fell out of the window (empty until Stage 3), and the recent messages inside the window,
 * oldest first.
 */
public record ConversationContext(Optional<String> summary, List<ChatMessageDto> recentMessages) {
}
