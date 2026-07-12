package app.ai.chat.feature;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class EmailWriteService extends AbstractChatFeatureService {

    public EmailWriteService(ChatClient.Builder chatClientBuilder) {
        super(chatClientBuilder,
                "You help draft clear, polite, professional emails based on the user's request. Include a subject line and a greeting/closing appropriate to the context.");
    }
}
