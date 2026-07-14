package app.ai.chat.history;

import app.ai.chat.dto.ChatMessageDto;
import app.ai.chat.dto.ChatSessionSummary;
import app.ai.chat.dto.ConversationContext;

import java.util.List;

/**
 * 대화 저장소의 단일 진입점. 호출자(컨트롤러, 기능 서비스)는 뒤에 있는 저장 계층을 전혀
 * 알지 못한다 — 1단계는 인메모리, 2단계는 Redis 추가, 3단계는 RDBMS 아카이브 + 롤링 요약
 * 추가. docs/chat-history-tiered-storage-design.md 참고.
 */
public interface ChatHistoryService {

    ChatSessionSummary createSession();

    List<ChatSessionSummary> findSessions();

    /** 모든 계층에서 세션을 제거한다 (메시지, 이후 단계에서는 아카이브 + 요약까지). */
    void deleteSession(String sessionId);

    /** 세션 제목을 갱신한다 — 첫 턴 완료 후 LLM이 요약해 만든 제목이 기본값("새 대화")을 대체한다. */
    void updateSessionTitle(String sessionId, String title);

    /**
     * 메시지를 추가하고 세션별 단조 증가 시퀀스 번호를 반환한다. 이 seq가 이후 단계에서
     * 계층 간 일관성을 지켜주는 기반이다 (멱등한 아카이빙, 복원 지점).
     */
    long appendMessage(String sessionId, String role, String content);

    /**
     * 프롬프트 조립용 뷰: 최신 롤링 요약(3단계 전까지는 비어 있음)과 가장 최근 윈도우의
     * 메시지들. 윈도우 크기는 설정값({@code app.chat.history.window-size})이며 호출자가
     * 정하는 것이 아니다.
     */
    ConversationContext getContext(String sessionId);

    /** 화면 표시용 전체 히스토리 (UI 세션 재로드), 오래된 순. */
    List<ChatMessageDto> findMessages(String sessionId);
}
