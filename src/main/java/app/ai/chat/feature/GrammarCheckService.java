package app.ai.chat.feature;

import app.ai.chat.ChatHistoryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class GrammarCheckService extends AbstractChatFeatureService {

    public GrammarCheckService(ChatClient.Builder chatClientBuilder, ChatHistoryService chatHistoryService) {
        super(chatClientBuilder, chatHistoryService,
                "You correct grammar and spelling mistakes in the user's text. Return the corrected text first, then briefly explain the changes.");
    }
}
