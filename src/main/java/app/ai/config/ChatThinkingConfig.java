package app.ai.config;

import org.springframework.ai.chat.client.ChatClientBuilderCustomizer;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 추론 과정 노출 옵트인 (docs/07-thinking-tool-status-design.md 7절).
 *
 * <p>플래그가 켜지면 모든 {@link org.springframework.ai.chat.client.ChatClient.Builder}에
 * Ollama {@code think} 옵션을 기본 적용한다 — 응답에 thinking 델타가 분리 수신되고
 * {@code AbstractChatFeatureService}가 THINKING 이벤트로 방출한다.
 *
 * <p><b>전제</b>: thinking 지원 모델(qwen3 계열 등)이어야 한다. 미지원 모델(qwen2.5 등)에
 * think를 요청하면 Ollama가 400으로 거부해 모든 대화가 실패하므로 기본값은 off다.
 * 저수준 {@code ChatModel}을 직접 쓰는 GENERAL_CHAT은 customizer 적용 대상이 아니다(의도).
 */
@Configuration
public class ChatThinkingConfig {

    @Bean
    @ConditionalOnProperty(name = "app.chat.thinking.enabled", havingValue = "true")
    ChatClientBuilderCustomizer thinkingCustomizer() {
        // 2.0의 defaultOptions는 완성된 옵션이 아니라 ChatOptions.Builder를 받는다 —
        // 모델별 기본 옵션과의 병합을 프레임워크가 지연 수행한다.
        return builder -> builder.defaultOptions(OllamaChatOptions.builder().enableThinking());
    }
}
