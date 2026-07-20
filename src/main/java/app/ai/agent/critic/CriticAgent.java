package app.ai.agent.critic;

import app.ai.agent.AgentTeamProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Component;

/**
 * 비평가 에이전트 — 비평가 루프의 검증 단계. 수퍼바이저의 초안을 워커 보고와 대조해
 * 사실 근거(수치 날조)·질문 커버리지·서식 규칙 위반만 점검하고, 문체·순서 같은 취향은
 * 건드리지 않는다. 판정만 하고 수정은 하지 않는다 — 수정은 수퍼바이저와 같은 시스템
 * 프롬프트를 가진 reviser가 한다 ({@link CriticLoop} 참고).
 *
 * <p>비평가 사용 여부는 기능 선택으로 갈린다 — CRITIC_AGENT_TEAM
 * ({@link CriticAgentTeamService})만 이 빈을 쓰고, AGENT_TEAM은 비평 없이 동작한다.
 */
@Component
public class CriticAgent {

    private static final String SYSTEM_PROMPT = """
            You are the critic of a Korean financial assistant team. You receive the
            user's question, the workers' fact digests, and a draft answer written by
            the team supervisor. Run exactly three checks:
            1. GROUNDING — every figure and factual claim in the draft must be
               supported by the digests. Flag fabricated, altered, or mismatched
               numbers and facts.
            2. COVERAGE — the draft must address every part of the question. If a
               worker failed or found nothing, the draft must say so honestly instead
               of inventing content.
            3. RULES — the draft must not name sources (no file/report/press names,
               no URLs, no "리포트에 따르면") and must not add as-of/기준일
               disclaimers, unless the user explicitly asked for them. Also flag any
               tool-call or delegation syntax left in the draft text (e.g.
               askMarketAnalyst({"task": ...})) — the user must never see it.
            Do NOT nitpick style, tone, wording, or ordering — judge only the three
            checks above. Minor issues that do not mislead the user are a PASS.
            If the draft is acceptable, reply with the single word: PASS
            Otherwise reply with a short numbered list, in Korean, of the concrete
            problems to fix — no rewrite, no praise, problems only.
            """;

    private final ChatClient chatClient;

    public CriticAgent(ChatClient.Builder chatClientBuilder, AgentTeamProperties properties) {
        ChatClient.Builder builder = chatClientBuilder.defaultSystem(SYSTEM_PROMPT);
        if (!properties.criticModel().isBlank()) {
            builder = builder.defaultOptions(ChatOptions.builder().model(properties.criticModel()));
        }
        this.chatClient = builder.build();
    }

    /**
     * 초안 1건을 검증한다 — 히스토리 없는 블로킹 LLM 호출 1회. 타임아웃·예외 격리는
     * 호출자({@link CriticLoop})가 맡는다. 빈 응답은 통과로 처리한다(fail-open).
     */
    public CriticVerdict review(String question, String digestReport, String draft) {
        String response = chatClient.prompt()
                .user("""
                        [사용자 질문]
                        %s

                        [워커 보고]
                        %s

                        [초안 답변]
                        %s
                        """.formatted(question, digestReport, draft))
                .call()
                .content();
        String verdict = response == null ? "" : response.strip();
        return verdict.isEmpty() || verdict.regionMatches(true, 0, "PASS", 0, 4)
                ? CriticVerdict.pass()
                : CriticVerdict.fail(verdict);
    }
}
