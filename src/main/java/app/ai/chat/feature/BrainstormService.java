package app.ai.chat.feature;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class BrainstormService extends AbstractChatFeatureService {

    public BrainstormService(ChatClient.Builder chatClientBuilder) {
        super(chatClientBuilder,
                "You brainstorm creative, diverse ideas based on the user's topic. Present them as a numbered list with a one-line explanation for each.");
    }
}
