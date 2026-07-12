package app.ai.chat.feature;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class CodeReviewService extends AbstractChatFeatureService {

    public CodeReviewService(ChatClient.Builder chatClientBuilder) {
        super(chatClientBuilder,
                "You are a senior software engineer performing a code review. Point out bugs, readability issues, and concrete, actionable improvement suggestions.");
    }
}
