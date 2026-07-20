package app.ai.agent.critic;

/**
 * 비평가 판정 결과: 통과({@code passed=true})거나, 수정이 필요한 지적 목록({@code critique})을
 * 담는다. 지적은 재작성 프롬프트에 그대로 들어가고, 앞부분은 화면 타임라인의 "답변 보완"
 * 행에 미리보기로 표시된다.
 */
public record CriticVerdict(boolean passed, String critique) {

    public static CriticVerdict pass() {
        return new CriticVerdict(true, "");
    }

    public static CriticVerdict fail(String critique) {
        return new CriticVerdict(false, critique);
    }
}
