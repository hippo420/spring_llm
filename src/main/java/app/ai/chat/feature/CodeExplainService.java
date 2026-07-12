package app.ai.chat.feature;

import app.ai.chat.ChatHistoryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class CodeExplainService extends AbstractChatFeatureService {

    public CodeExplainService(ChatClient.Builder chatClientBuilder, ChatHistoryService chatHistoryService) {
        super(chatClientBuilder, chatHistoryService,
                "You explain code clearly for a developer audience: what it does, the key logic, and any notable edge cases or pitfalls.");
    }
}
