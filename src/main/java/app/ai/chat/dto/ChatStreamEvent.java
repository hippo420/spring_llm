package app.ai.chat.dto;

/**
 * 스트리밍 응답의 단일 이벤트 (docs/07-thinking-tool-status-design.md 4·5.1절).
 *
 * <ul>
 *   <li>{@link Type#TOKEN} — 답변 텍스트 델타. 히스토리 저장·제목 생성의 대상.</li>
 *   <li>{@link Type#THINKING} — 추론(thinking) 텍스트 델타. 실시간 표시용, 저장하지 않는다.</li>
 *   <li>{@link Type#STATUS} — 앱이 지금 수행 중인 단계의 사용자용 안내 문구
 *       ("문서 검색 중..." 등, 문자만 — 아이콘 없음). 모델 능력과 무관하게 서버 코드가
 *       발행한다. 화면에는 현재 작업 하나만 말풍선 아래에 표시되고 답변 완료 시 사라진다.</li>
 *   <li>{@link Type#SOURCES} — 답변이 참조한 문서 파일명 목록(개행 구분). 답변 완료 후
 *       말풍선 아래에 확장자별 아이콘과 함께 표시된다. 저장하지 않는다.</li>
 *   <li>{@link Type#TOOL} — 도구 호출 상태 JSON 한 줄. 실시간 표시용, 저장하지 않는다.</li>
 * </ul>
 */
public record ChatStreamEvent(Type type, String data) {

    public enum Type { TOKEN, THINKING, STATUS, SOURCES, TOOL }

    public static ChatStreamEvent token(String data) {
        return new ChatStreamEvent(Type.TOKEN, data);
    }

    public static ChatStreamEvent thinking(String data) {
        return new ChatStreamEvent(Type.THINKING, data);
    }

    public static ChatStreamEvent status(String data) {
        return new ChatStreamEvent(Type.STATUS, data);
    }

    public static ChatStreamEvent sources(String newlineSeparatedFilenames) {
        return new ChatStreamEvent(Type.SOURCES, newlineSeparatedFilenames);
    }

    public static ChatStreamEvent tool(String json) {
        return new ChatStreamEvent(Type.TOOL, json);
    }
}
