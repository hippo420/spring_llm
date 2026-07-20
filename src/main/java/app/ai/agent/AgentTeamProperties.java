package app.ai.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 멀티 에이전트 설정 (docs/13-multi-agent-design.md 6절). 모델은 기본값(빈 문자열)이면
 * 기본 챗 모델({@code spring.ai.ollama.chat.model})을 공유한다 — 단일 GPU에서 서로 다른
 * 모델을 오가면 로드/축출 스왑이 생기므로 분리는 실험용 옵트인이다.
 */
@Component
public class AgentTeamProperties {

    private final String supervisorModel;
    private final String workerModel;
    private final String criticModel;
    private final int maxWorkerCalls;
    private final long workerTimeoutSeconds;

    public AgentTeamProperties(
            @Value("${app.agent.supervisor-model:}") String supervisorModel,
            @Value("${app.agent.worker-model:}") String workerModel,
            @Value("${app.agent.critic-model:}") String criticModel,
            @Value("${app.agent.max-worker-calls:4}") int maxWorkerCalls,
            @Value("${app.agent.worker-timeout-seconds:120}") long workerTimeoutSeconds) {
        this.supervisorModel = supervisorModel.strip();
        this.workerModel = workerModel.strip();
        this.criticModel = criticModel.strip();
        this.maxWorkerCalls = maxWorkerCalls;
        this.workerTimeoutSeconds = workerTimeoutSeconds;
    }

    public String supervisorModel() {
        return supervisorModel;
    }

    public String workerModel() {
        return workerModel;
    }

    /** 비평가 전용 모델 — 빈 값이면 기본 챗 모델 공유. 비평가 온오프는 기능 선택
     * (CRITIC_AGENT_TEAM vs AGENT_TEAM)으로 한다. */
    public String criticModel() {
        return criticModel;
    }

    /** 사용자 턴당 워커 위임 상한 — 초과분은 실행 없이 상한 안내 문자열을 반환한다 (설계 4절). */
    public int maxWorkerCalls() {
        return maxWorkerCalls;
    }

    public long workerTimeoutSeconds() {
        return workerTimeoutSeconds;
    }
}
