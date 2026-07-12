package app.ai.chat.feature;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class SentimentAnalysisService extends AbstractChatFeatureService {

    public SentimentAnalysisService(ChatClient.Builder chatClientBuilder) {
        super(chatClientBuilder,
                "You analyze the sentiment and tone of the user's text (e.g. positive/negative/neutral, emotions present) and briefly explain your reasoning.");
    }
}
