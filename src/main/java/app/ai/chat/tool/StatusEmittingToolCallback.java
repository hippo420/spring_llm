package app.ai.chat.tool;

import app.ai.chat.dto.ChatStreamEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 도구 호출 상태 발행 데코레이터 (docs/07-thinking-tool-status-design.md 5.3절):
 * 도구 실행 시작/종료(성공·실패·소요시간)를 TOOL 이벤트로 발행해 사용자 화면에
 * "searchDocuments 실행 중..." 진행 표시를 만든다. Spring AI의 내부 도구 루프는
 * 스트림에 아무것도 내보내지 않으므로 도구 자체를 감싼다.
 *
 * <p>이벤트 발행 콜백은 요청 스코프인 {@link ToolContext}로 전달받는다 — 도구 빈은
 * 싱글턴이지만 동시 요청 간 이벤트가 섞이지 않는다. 콜백이 없으면 조용히 무시한다.
 * 도구 호출은 곧 외부 접근이므로 INFO 감사 로그도 여기서 남긴다 (설계 09의 7절).
 */
public final class StatusEmittingToolCallback implements ToolCallback {

    /** ToolContext 키 — {@code Consumer<ChatStreamEvent>} 타입의 이벤트 발행 콜백. */
    public static final String EMITTER_KEY = "chatStreamEventEmitter";

    private static final Logger log = LoggerFactory.getLogger(StatusEmittingToolCallback.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int ARGS_PREVIEW_LIMIT = 200;

    private final ToolCallback delegate;

    private StatusEmittingToolCallback(ToolCallback delegate) {
        this.delegate = delegate;
    }

    public static List<ToolCallback> wrapAll(ToolCallback[] callbacks) {
        return Arrays.stream(callbacks)
                .<ToolCallback>map(StatusEmittingToolCallback::new)
                .toList();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        Consumer<ChatStreamEvent> emitter = emitterFrom(toolContext);
        String name = delegate.getToolDefinition().name();

        Map<String, Object> start = new LinkedHashMap<>();
        start.put("name", name);
        start.put("phase", "start");
        start.put("args", truncate(toolInput));
        emit(emitter, start);

        long startedAt = System.currentTimeMillis();
        try {
            String result = delegate.call(toolInput, toolContext);
            long elapsed = System.currentTimeMillis() - startedAt;
            log.info("도구 호출 성공: {} ({}ms, args={})", name, elapsed, truncate(toolInput));
            emit(emitter, endEvent(name, true, elapsed, null));
            return result;
        } catch (RuntimeException e) {
            long elapsed = System.currentTimeMillis() - startedAt;
            log.warn("도구 호출 실패: {} ({}ms): {}", name, elapsed, e.getMessage());
            emit(emitter, endEvent(name, false, elapsed, e.getMessage()));
            throw e;
        }
    }

    private static Map<String, Object> endEvent(String name, boolean ok, long elapsed, String error) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("name", name);
        event.put("phase", "end");
        event.put("ok", ok);
        event.put("durationMs", elapsed);
        if (error != null) {
            event.put("error", truncate(error));
        }
        return event;
    }

    @SuppressWarnings("unchecked")
    private static Consumer<ChatStreamEvent> emitterFrom(ToolContext toolContext) {
        if (toolContext == null) {
            return null;
        }
        Object emitter = toolContext.getContext().get(EMITTER_KEY);
        return emitter instanceof Consumer ? (Consumer<ChatStreamEvent>) emitter : null;
    }

    private static void emit(Consumer<ChatStreamEvent> emitter, Map<String, Object> payload) {
        if (emitter == null) {
            return;
        }
        try {
            // SSE data는 개행을 담을 수 없다 — Jackson 직렬화는 한 줄을 보장한다 (설계 4절).
            emitter.accept(ChatStreamEvent.tool(JSON.writeValueAsString(payload)));
        } catch (Exception e) {
            log.debug("도구 상태 이벤트 직렬화 실패 — 표시만 생략: {}", e.getMessage());
        }
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= ARGS_PREVIEW_LIMIT ? text : text.substring(0, ARGS_PREVIEW_LIMIT) + "…";
    }
}
