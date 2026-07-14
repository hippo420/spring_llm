package app.ai.chat.feature;

import app.ai.chat.history.ChatHistoryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class SummarizeService extends AbstractChatFeatureService {

    public SummarizeService(ChatClient.Builder chatClientBuilder, ChatHistoryService chatHistoryService) {
        super(chatClientBuilder, chatHistoryService,
                "You summarize the user's text concisely, capturing only the key points as a short list or paragraph. Respond in the same language as the input.");
    }
}
