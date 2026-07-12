package app.ai.chat.feature;

import app.ai.chat.ChatHistoryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class CodeReviewService extends AbstractChatFeatureService {

    public CodeReviewService(ChatClient.Builder chatClientBuilder, ChatHistoryService chatHistoryService) {
        super(chatClientBuilder, chatHistoryService,
                "You are a senior software engineer performing a code review. Point out bugs, readability issues, and concrete, actionable improvement suggestions.");
    }
}
