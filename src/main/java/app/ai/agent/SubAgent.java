package app.ai.agent;

import app.ai.chat.tool.StatusEmittingToolCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 워커 에이전트 공통 베이스 (docs/13-multi-agent-design.md 3.3절): 좁은 시스템 프롬프트와
 * 도구 1~2종만 가진 전용 {@link ChatClient}로 하위 과제를 동기 실행하고 <b>압축 요약</b>만
 * 반환한다 — 도구 원문은 워커 컨텍스트에서 소화되고 수퍼바이저 컨텍스트에는 들어가지 않는다.
 *
 * <p>각 워커 서브클래스는 자기만의 {@code @Tool} 메서드(askMarketAnalyst 등)를 노출하고
 * 본문은 {@link #delegate}에 위임한다 — 설계의 SubAgentTools 파사드 대신 워커가 직접 도구가
 * 되는 방식이라, 조건부 워커(웹 리서처)가 빈 등록 여부만으로 자연히 붙고 빠진다.
 *
 * <p>가드레일(설계 4절)은 전부 여기서 공통 적용된다: 턴당 위임 상한(수퍼바이저가
 * {@link ToolContext}에 심은 카운터), 워커 타임아웃, 실패 격리(예외를 던지지 않고
 * 실패 문자열 반환 — 수퍼바이저가 나머지 결과로 계속 답한다). 워커는 다른 워커를
 * 도구로 갖지 않으므로 재귀 위임은 구조적으로 불가능하다.
 */
public abstract class SubAgent {

    /** ToolContext 키 — 수퍼바이저가 요청마다 심는 {@link AtomicInteger} 위임 카운터. */
    public static final String CALL_COUNT_KEY = "agentWorkerCallCount";

    /**
     * ToolContext 키 — 이번 턴의 워커 보고(과제+결과)를 모으는 {@code List<String>}.
     * 비평가 루프({@code app.ai.agent.critic})가 초안을 사실 대조할 때 원본 자료로 쓴다.
     * 키가 없으면(비평가 비활성) 수집하지 않는다.
     */
    public static final String DIGEST_LOG_KEY = "agentWorkerDigestLog";

    private static final Logger log = LoggerFactory.getLogger(SubAgent.class);

    /**
     * 워커 실행 전용 풀 — 타임아웃을 걸려면 블로킹 LLM 호출을 별도 스레드에서 돌려야 한다.
     * 워커 호출은 요청당 최대 {@code max-worker-calls}회의 저빈도 블로킹 IO이므로 가상
     * 스레드가 적합하다. 타임아웃된 호출의 스레드는 회수하지 않는다(Ollama HTTP가 끝나면
     * 스스로 소멸) — 결과만 버려진다.
     */
    private static final ExecutorService WORKER_POOL = Executors.newVirtualThreadPerTaskExecutor();

    private final ChatClient chatClient;
    private final List<ToolCallback> tools;
    private final String displayName;
    private final AgentTeamProperties properties;

    protected SubAgent(ChatClient.Builder chatClientBuilder, AgentTeamProperties properties,
                       String displayName, String systemPrompt, Object... toolBeans) {
        ChatClient.Builder builder = chatClientBuilder.defaultSystem(systemPrompt);
        if (!properties.workerModel().isBlank()) {
            // 2.0의 defaultOptions는 완성된 옵션이 아니라 Builder를 받는다
            builder = builder.defaultOptions(ChatOptions.builder().model(properties.workerModel()));
        }
        this.chatClient = builder.build();
        // 워커 내부 도구도 래핑한다 — 워커 실행 중의 도구 진행(TOOL 이벤트)이 같은 SSE
        // 스트림으로 사용자 화면에 올라간다 (설계 5절).
        this.tools = StatusEmittingToolCallback.wrapAll(ToolCallbacks.from(toolBeans));
        this.displayName = displayName;
        this.properties = properties;
    }

    /**
     * 서브클래스의 {@code @Tool} 메서드가 호출하는 공통 진입점 — 가드레일을 적용해 하위
     * 과제를 실행한다. 반환값은 항상 문자열이다(실패 포함) — 설계 09의 7절과 같은 원칙으로
     * 예외를 수퍼바이저에게 던지지 않는다.
     */
    protected String delegate(String task, ToolContext toolContext) {
        String digest = doDelegate(task, toolContext);
        // 실패 문자열도 기록한다 — 비평가가 "왜 이 내용이 초안에 없는지"를 알아야
        // 누락을 날조로 오판하지 않는다.
        if (toolContext.getContext().get(DIGEST_LOG_KEY) instanceof List<?> entries) {
            @SuppressWarnings("unchecked")
            List<String> digestLog = (List<String>) entries;
            digestLog.add("[%s] 과제: %s\n보고: %s".formatted(displayName, task, digest));
        }
        return digest;
    }

    private String doDelegate(String task, ToolContext toolContext) {
        Object counter = toolContext.getContext().get(CALL_COUNT_KEY);
        if (counter instanceof AtomicInteger count
                && count.incrementAndGet() > properties.maxWorkerCalls()) {
            log.warn("워커 위임 상한 초과: {} (상한 {}회) — 실행 없이 반환", displayName, properties.maxWorkerCalls());
            return "[위임 상한 초과] 이번 턴의 워커 호출 한도를 다 썼다 — 지금까지 확보한 정보만으로 답하라.";
        }

        long startedAt = System.currentTimeMillis();
        CompletableFuture<String> future =
                CompletableFuture.supplyAsync(() -> run(task, toolContext.getContext()), WORKER_POOL);
        try {
            String digest = future.get(properties.workerTimeoutSeconds(), TimeUnit.SECONDS);
            log.info("워커 완료: {} ({}ms, task={})", displayName, System.currentTimeMillis() - startedAt, task);
            return digest == null || digest.isBlank()
                    ? "[워커 실패] " + displayName + ": 빈 응답 — 이 하위 과제 없이 답하라."
                    : digest;
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("워커 시간 초과: {} ({}s)", displayName, properties.workerTimeoutSeconds());
            return "[워커 실패] " + displayName + ": " + properties.workerTimeoutSeconds()
                    + "초 안에 끝나지 않았다 — 이 하위 과제 없이 답하라.";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "[워커 실패] " + displayName + ": 실행이 중단됐다 — 이 하위 과제 없이 답하라.";
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.warn("워커 실패: {} ({}ms): {}", displayName, System.currentTimeMillis() - startedAt, cause.toString());
            return "[워커 실패] " + displayName + ": " + cause.getMessage() + " — 이 하위 과제 없이 답하라.";
        }
    }

    /**
     * 하위 과제 1건의 실제 실행 — 히스토리 없는 단발 호출이다(워커는 무상태, 컨텍스트 격리).
     * 부모 {@link ToolContext} 내용을 그대로 전달하므로 워커 안의 도구도 기존 세션 격리
     * ({@code sessionId})와 SOURCES·TOOL 이벤트 발행({@code emitter})이 동일하게 동작한다.
     * 자식 컨텍스트에는 워커 표시명을 추가로 심는다 — 워커 내부 도구의 TOOL 이벤트에 소속
     * 에이전트가 태깅되어, 화면의 에이전트 타임라인이 실행 순서 추론 없이 워커별로 묶인다.
     */
    private String run(String task, Map<String, Object> parentContext) {
        Map<String, Object> workerContext = new HashMap<>(parentContext);
        workerContext.put(StatusEmittingToolCallback.AGENT_NAME_KEY, displayName);
        return chatClient.prompt()
                .user(task)
                .tools((Object[]) tools.toArray(ToolCallback[]::new))
                .toolContext(workerContext)
                .call()
                .content();
    }
}
