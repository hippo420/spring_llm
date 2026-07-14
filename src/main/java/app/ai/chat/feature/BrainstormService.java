package app.ai.chat.feature;

import app.ai.chat.history.ChatHistoryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class BrainstormService extends AbstractChatFeatureService {

    public BrainstormService(ChatClient.Builder chatClientBuilder, ChatHistoryService chatHistoryService) {
        super(chatClientBuilder, chatHistoryService,
                "You brainstorm creative, diverse ideas based on the user's topic. Present them as a numbered list with a one-line explanation for each.");
    }
}
