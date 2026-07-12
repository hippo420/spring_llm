package app.ai.chat.feature;

import app.ai.chat.ChatHistoryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * TODO: wire up RAG retrieval (pgvector + RetrievalAugmentedGenerationAdvisor) so answers are
 * grounded in uploaded documents. Currently answers from the model's general knowledge only.
 */
@Service
public class DocQaService extends AbstractChatFeatureService {

    public DocQaService(ChatClient.Builder chatClientBuilder, ChatHistoryService chatHistoryService) {
        super(chatClientBuilder, chatHistoryService,
                "You answer questions as helpfully as possible. If the user refers to an attached document, note that document-grounded retrieval is not yet available and answer from general knowledge instead.");
    }
}
