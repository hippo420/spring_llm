package app.ai.chat.feature;

import app.ai.chat.history.ChatHistoryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class EmailWriteService extends AbstractChatFeatureService {

    public EmailWriteService(ChatClient.Builder chatClientBuilder, ChatHistoryService chatHistoryService) {
        super(chatClientBuilder, chatHistoryService,
                "You help draft clear, polite, professional emails based on the user's request. Include a subject line and a greeting/closing appropriate to the context.");
    }
}
