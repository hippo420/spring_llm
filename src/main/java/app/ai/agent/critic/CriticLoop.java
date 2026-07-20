package app.ai.agent.critic;

import app.ai.agent.AgentTeamProperties;
import app.ai.chat.dto.ChatStreamEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 비평가 루프 오케스트레이션: 초안 → {@link CriticAgent} 검증 → 필요 시 reviser가 1회
 * 재작성. 왕복은 최대 1회다 — 재작성본을 다시 검증하지 않는다(단일 GPU에서 왕복마다
 * 수십 초가 곱해지므로, 비용 대비 효과가 가장 좋은 지점에서 멈춘다).
 *
 * <p>가드레일은 워커({@code SubAgent})와 같은 원칙: 각 LLM 호출에 타임아웃, 실패 격리 —
 * 검증이든 재작성이든 실패하면 초안을 그대로 반환한다(fail-open, 비평가가 답변을 막는
 * 단일 장애점이 되지 않는다). 진행 상황은 TOOL/STATUS 이벤트로 발행해 화면 타임라인에
 * "비평가"·"답변 보완" 행이 워커들과 같은 방식으로 표시된다.
 */
@Component
public class CriticLoop {

    private static final Logger log = LoggerFactory.getLogger(CriticLoop.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int CRITIQUE_PREVIEW_LIMIT = 80;

    /**
     * 재작성본 폐기 조건 — 도구 없는 reviser가 위임 구문을 텍스트로 써버린 경우
     * (예: {@code askMarketAnalyst({"task": ...})}). 이런 재작성본은 초안보다 나쁘므로
     * 버리고 초안을 반환한다.
     */
    private static final Pattern DELEGATION_SYNTAX =
            Pattern.compile("ask(MarketAnalyst|DocumentAnalyst|NewsAnalyst|WebResearcher)");

    /** 타임아웃을 걸기 위한 실행 풀 — 워커와 같은 이유로 가상 스레드 (SubAgent 주석 참고). */
    private static final ExecutorService CRITIC_POOL = Executors.newVirtualThreadPerTaskExecutor();

    private final CriticAgent criticAgent;
    private final AgentTeamProperties properties;

    public CriticLoop(CriticAgent criticAgent, AgentTeamProperties properties) {
        this.criticAgent = criticAgent;
        this.properties = properties;
    }

    /**
     * 초안을 검증하고 사용자에게 내보낼 최종 답변 텍스트를 반환한다.
     *
     * @param reviser 재작성용 클라이언트 — 수퍼바이저와 같은 시스템 프롬프트(서식·출처
     *                억제 규칙)를 가져야 수정본이 초안과 같은 목소리로 나온다.
     */
    public String finalize(String question, List<String> digests, String draft,
                           ChatClient reviser, Consumer<ChatStreamEvent> emitter) {
        String digestReport = String.join("\n\n", digests);

        emitter.accept(ChatStreamEvent.status("비평가 검토 중..."));
        emitTool(emitter, startEvent("criticReview", "초안 사실 검증"));
        long reviewStartedAt = System.currentTimeMillis();
        CriticVerdict verdict;
        try {
            verdict = withTimeout(() -> criticAgent.review(question, digestReport, draft));
        } catch (Exception e) {
            log.warn("비평가 검토 실패 — 초안 그대로 반환: {}", e.toString());
            emitTool(emitter, endEvent("criticReview", false, elapsed(reviewStartedAt)));
            return draft;
        }
        emitTool(emitter, endEvent("criticReview", true, elapsed(reviewStartedAt)));
        log.info("비평가 판정: {} ({}ms{})", verdict.passed() ? "PASS" : "수정 필요",
                elapsed(reviewStartedAt), verdict.passed() ? "" : ", 지적=" + preview(verdict.critique()));
        if (verdict.passed()) {
            return draft;
        }

        emitter.accept(ChatStreamEvent.status("답변 보완 중..."));
        emitTool(emitter, startEvent("reviseAnswer", preview(verdict.critique())));
        long reviseStartedAt = System.currentTimeMillis();
        try {
            String revised = withTimeout(() -> reviser.prompt()
                    .user(revisionPrompt(question, digestReport, draft, verdict.critique()))
                    .call()
                    .content());
            boolean usable = revised != null && !revised.isBlank()
                    && !DELEGATION_SYNTAX.matcher(revised).find();
            if (!usable && revised != null && !revised.isBlank()) {
                log.warn("재작성본에 위임 구문 잔존 — 폐기하고 초안 반환: {}", preview(revised));
            }
            emitTool(emitter, endEvent("reviseAnswer", usable, elapsed(reviseStartedAt)));
            return usable ? revised.strip() : draft;
        } catch (Exception e) {
            log.warn("답변 재작성 실패 — 초안 그대로 반환: {}", e.toString());
            emitTool(emitter, endEvent("reviseAnswer", false, elapsed(reviseStartedAt)));
            return draft;
        }
    }

    private static String revisionPrompt(String question, String digestReport, String draft, String critique) {
        return """
                [사용자 질문]
                %s

                [워커 보고]
                %s

                [초안 답변]
                %s

                [비평가 지적]
                %s

                Rewrite the draft answer so that every critic issue is fixed. Keep all
                your system rules: ground claims ONLY in the worker digests, keep the
                Markdown structure rules, no source names, no as-of disclaimers,
                answer in Korean. Output ONLY the corrected final answer — no preamble,
                no explanation of what you changed.
                """.formatted(question, digestReport, draft, critique);
    }

    private <T> T withTimeout(Supplier<T> task) throws Exception {
        CompletableFuture<T> future = CompletableFuture.supplyAsync(task, CRITIC_POOL);
        try {
            return future.get(properties.workerTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            future.cancel(true);
            throw e;
        }
    }

    // TOOL 이벤트는 StatusEmittingToolCallback과 같은 JSON 형태로 발행한다 — 프론트
    // 타임라인이 도구 이벤트와 구분 없이 소비한다 (name/phase/args/ok/durationMs).
    private static Map<String, Object> startEvent(String name, String task) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("name", name);
        event.put("phase", "start");
        try {
            event.put("args", JSON.writeValueAsString(Map.of("task", task)));
        } catch (Exception ignored) {
            // args는 표시용 — 직렬화 실패 시 생략
        }
        return event;
    }

    private static Map<String, Object> endEvent(String name, boolean ok, long elapsed) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("name", name);
        event.put("phase", "end");
        event.put("ok", ok);
        event.put("durationMs", elapsed);
        return event;
    }

    private static void emitTool(Consumer<ChatStreamEvent> emitter, Map<String, Object> payload) {
        try {
            emitter.accept(ChatStreamEvent.tool(JSON.writeValueAsString(payload)));
        } catch (Exception e) {
            log.debug("비평가 상태 이벤트 직렬화 실패 — 표시만 생략: {}", e.getMessage());
        }
    }

    private static long elapsed(long startedAt) {
        return System.currentTimeMillis() - startedAt;
    }

    private static String preview(String critique) {
        String flat = critique.replaceAll("\\s+", " ").strip();
        return flat.length() <= CRITIQUE_PREVIEW_LIMIT ? flat : flat.substring(0, CRITIQUE_PREVIEW_LIMIT) + "…";
    }
}
