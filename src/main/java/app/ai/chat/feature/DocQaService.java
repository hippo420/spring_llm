package app.ai.chat.feature;

import app.ai.chat.history.ChatHistoryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * TODO: 업로드된 문서에 근거한 답변이 되도록 RAG 검색(pgvector +
 * RetrievalAugmentedGenerationAdvisor)을 연결할 것. 현재는 모델의 일반 지식으로만 답변한다.
 */
@Service
public class DocQaService extends AbstractChatFeatureService {

    public DocQaService(ChatClient.Builder chatClientBuilder, ChatHistoryService chatHistoryService) {
        super(chatClientBuilder, chatHistoryService,
                "You answer questions as helpfully as possible. If the user refers to an attached document, note that document-grounded retrieval is not yet available and answer from general knowledge instead.");
    }
}
