package app.ai.chat.history;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 세션 제목 생성기 — 첫 턴(사용자 질문 + 어시스턴트 답변)이 끝나면 그 내용을 LLM으로
 * 요약해 짧은 제목을 만들고, 기본 제목("새 대화")을
 * {@link ChatHistoryService#updateSessionTitle}로 교체한다.
 *
 * <p>생성은 비동기로 실행된다 — 응답 스트리밍이 끝난 뒤(GPU 유휴 시점)에 돌고, 실패해도
 * 경고 로그만 남긴다. 제목은 부가 정보라 대화 흐름을 절대 막지 않는다.
 */
@Component
public class ChatSessionTitleGenerator {

    private static final Logger log = LoggerFactory.getLogger(ChatSessionTitleGenerator.class);

    /** LLM에 넘길 첫 턴 본문의 최대 길이 — 제목 뽑는 데 전문이 필요 없다. */
    private static final int SOURCE_SNIPPET_LENGTH = 500;
    /** 생성된 제목의 최대 길이 (chat_session.title은 VARCHAR(200)). */
    private static final int MAX_TITLE_LENGTH = 50;

    private static final String SYSTEM_PROMPT = """
            너는 대화 제목 생성기다. 주어진 첫 대화(사용자 질문과 어시스턴트 답변)를 보고
            대화 목록에 표시할 제목을 만든다.
            규칙: 한국어 명사구로 20자 이내. 따옴표, 마침표, 이모지, 부가 설명 없이
            제목 텍스트만 한 줄로 출력한다.
            """;

    private final ChatClient chatClient;
    private final ChatHistoryService chatHistoryService;

    public ChatSessionTitleGenerator(ChatClient.Builder chatClientBuilder,
                                     ChatHistoryService chatHistoryService) {
        this.chatClient = chatClientBuilder.build();
        this.chatHistoryService = chatHistoryService;
    }

    /** 첫 턴 내용으로 제목을 만들어 세션에 반영한다. 호출 스레드를 막지 않는다. */
    public void generateAsync(String sessionId, String userMessage, String assistantReply) {
        Mono.fromRunnable(() -> generate(sessionId, userMessage, assistantReply))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(ignored -> {
                }, e -> log.warn("세션 제목 생성 실패 — 기본 제목 유지 (session={}): {}",
                        sessionId, e.getMessage()));
    }

    private void generate(String sessionId, String userMessage, String assistantReply) {
        String firstTurn = "사용자: " + snippet(userMessage) + "\n어시스턴트: " + snippet(assistantReply);
        String title = sanitize(chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(firstTurn)
                .call()
                .content());
        if (!title.isBlank()) {
            chatHistoryService.updateSessionTitle(sessionId, title);
            log.debug("세션 제목 생성 (session={}): {}", sessionId, title);
        }
    }

    private static String snippet(String text) {
        String stripped = text == null ? "" : text.strip();
        return stripped.length() <= SOURCE_SNIPPET_LENGTH
                ? stripped
                : stripped.substring(0, SOURCE_SNIPPET_LENGTH);
    }

    /** 모델이 규칙을 어겨도 제목답게 다듬는다: 첫 줄만, 감싼 따옴표·끝 마침표 제거, 길이 제한. */
    private static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String title = raw.strip();
        int newline = title.indexOf('\n');
        if (newline >= 0) {
            title = title.substring(0, newline);
        }
        title = title.replaceAll("^[\"'“”‘’`\\s]+", "")
                .replaceAll("[\"'“”‘’`.。\\s]+$", "");
        if (title.length() > MAX_TITLE_LENGTH) {
            title = title.substring(0, MAX_TITLE_LENGTH).strip();
        }
        return title;
    }
}
