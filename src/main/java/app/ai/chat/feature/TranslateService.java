package app.ai.chat.feature;

import app.ai.chat.ChatHistoryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class TranslateService extends AbstractChatFeatureService {

    public TranslateService(ChatClient.Builder chatClientBuilder, ChatHistoryService chatHistoryService) {
        super(chatClientBuilder, chatHistoryService,
                "You are a translation assistant. Translate Korean input into natural English, and non-Korean input into natural Korean, unless the user explicitly requests a different target language. Return only the translation.");
    }
}
