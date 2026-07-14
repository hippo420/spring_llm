package app.ai.chat.feature.normal;

import app.ai.chat.feature.ChatFeatureService;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

/**
 * 영속성 없는 계층의 공통 베이스: {@link ChatClient} 플루언트 API 기반이지만 대화
 * 히스토리를 사용하지 않고, 기본 시스템 프롬프트 + 현재 사용자 메시지 하나로만
 * 응답한다. 영속성(멀티턴 메모리)이 필요한 기능은
 * {@link app.ai.chat.feature.AbstractChatFeatureService}를 상속할 것.
 */
abstract class AbstractChatNormalService implements ChatFeatureService {

    private final ChatClient chatClient;

    protected AbstractChatNormalService(ChatClient.Builder chatClientBuilder, String systemPrompt) {
        this.chatClient = chatClientBuilder.defaultSystem(systemPrompt).build();
    }

    @Override
    public Flux<String> stream(String userMessage) {
        return chatClient.prompt().user(userMessage).stream().content();
    }
}
