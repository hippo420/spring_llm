package app.ai.chat.feature;

import app.ai.chat.dto.ChatStreamEvent;
import reactor.core.publisher.Flux;

public interface ChatFeatureService {

    /**
     * 영속성 없는 단발 스트리밍. 세션/히스토리를 전혀 사용하지 않고 현재 사용자
     * 메시지 하나로만 응답한다. (예: {@link app.ai.chat.feature.normal.GeneralChatService})
     */
    default Flux<String> stream(String userMessage) {
        throw new UnsupportedOperationException("이 기능은 세션(영속성) 기반 스트리밍만 지원한다.");
    }

    /**
     * 영속성(대화 히스토리) 기반 스트리밍. 계약: 호출자가 현재 사용자 메시지를 이미
     * {@link app.ai.chat.history.ChatHistoryService}에 추가했으므로, 세션 컨텍스트는
     * {@code userMessage}로 끝난다. 기본 구현은 히스토리를 무시하고 단발 스트리밍에
     * 위임한다 — 영속성을 지원하는 서비스는 이 메서드를 오버라이드한다.
     */
    default Flux<String> stream(String sessionId, String userMessage) {
        return stream(userMessage);
    }

    /**
     * 영속성 기반 스트리밍의 이벤트 버전 (docs/07-thinking-tool-status-design.md 5.1절):
     * 답변 토큰(TOKEN) 외에 추론 델타(THINKING)·도구 상태(TOOL)를 함께 흘릴 수 있다.
     * 기본 구현은 기존 {@link #stream(String, String)}을 TOKEN 이벤트로 감싼다 —
     * 확장 채널이 필요한 서비스만 오버라이드한다.
     */
    default Flux<ChatStreamEvent> streamEvents(String sessionId, String userMessage) {
        return stream(sessionId, userMessage).map(ChatStreamEvent::token);
    }
}
