package app.ai.chat.feature;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class SummarizeService extends AbstractChatFeatureService {

    public SummarizeService(ChatClient.Builder chatClientBuilder) {
        super(chatClientBuilder,
                "You summarize the user's text concisely, capturing only the key points as a short list or paragraph. Respond in the same language as the input.");
    }
}
