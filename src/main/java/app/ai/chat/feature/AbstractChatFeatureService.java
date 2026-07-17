package app.ai.chat.feature;

import app.ai.chat.history.ChatHistoryService;
import app.ai.chat.dto.ChatMessageDto;
import app.ai.chat.dto.ChatStreamEvent;
import app.ai.chat.dto.ConversationContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 영속성(대화 히스토리) 계층의 공통 베이스: {@link ChatClient} 플루언트 API 기반
 * (서비스마다 프로토타입 스코프의 {@link ChatClient.Builder}를 받으므로 기능별로 고유한
 * 기본 시스템 프롬프트를 유지한다). 영속성 없는 단발 계층은
 * {@link app.ai.chat.feature.normal.GeneralChatService},
 * 저수준 {@code ChatModel}/{@code Prompt}/{@code Message} API와의 대응 관계는
 * docs/01-spring-ai-chatmodel-vs-chatclient.md 참고.
 *
 * <p>멀티턴 메모리: 대화 윈도우 전체(요약 + 최근 메시지, 현재 사용자 메시지로 끝남 —
 * {@link ChatFeatureService#stream(String, String)} 계약 참고)를 {@code messages()}로
 * 전달하고, 기본 시스템 프롬프트는 클라이언트가 앞에 붙인다.
 */
public abstract class AbstractChatFeatureService implements ChatFeatureService {

    private final ChatClient chatClient;
    private final ChatHistoryService chatHistoryService;

    protected AbstractChatFeatureService(ChatClient.Builder chatClientBuilder,
                                         ChatHistoryService chatHistoryService,
                                         String systemPrompt) {
        this.chatClient = chatClientBuilder.defaultSystem(systemPrompt).build();
        this.chatHistoryService = chatHistoryService;
    }

    /**
     * OllamaChatModel이 thinking 델타를 출력 메시지 메타데이터에 넣을 때 쓰는 키.
     * 원본 상수({@code OllamaChatModel.THINKING_METADATA_KEY})가 private이라 값을 복제한다 —
     * Spring AI 버전 업 시 값 유지 여부를 재확인할 것 (docs/07-thinking-tool-status-design.md 5.2절).
     */
    private static final String THINKING_METADATA_KEY = "thinking";

    @Override
    public Flux<String> stream(String sessionId, String userMessage) {
        return streamEvents(sessionId, userMessage)
                .filter(event -> event.type() == ChatStreamEvent.Type.TOKEN)
                .map(ChatStreamEvent::data);
    }

    /**
     * {@code .stream().content()} 대신 {@code chatResponse()}로 청크 전체를 받아
     * 답변 텍스트(TOKEN)와 추론 메타데이터(THINKING)를 분리해 방출한다.
     * thinking은 지원 모델 + {@code app.chat.thinking.enabled=true}일 때만 존재하고,
     * 없으면 TOKEN만 흐른다 — 기존 동작과 동일하다.
     *
     * <p>준비 단계(히스토리 로드 + {@link #augment})는 구독 후 별도 스레드에서 실행한다 —
     * augment가 상태 콜백으로 발행하는 STATUS 이벤트("문서 검색 중..." 등)가 그 작업이
     * 끝나기 <b>전에</b> 클라이언트에 도달해야 진행 표시로서 의미가 있기 때문이다.
     */
    @Override
    public Flux<ChatStreamEvent> streamEvents(String sessionId, String userMessage) {
        Sinks.Many<ChatStreamEvent> statusSink = Sinks.many().unicast().onBackpressureBuffer();

        Mono<List<Message>> prepared = Mono.fromCallable(() -> {
                    ConversationContext context = chatHistoryService.getContext(sessionId);
                    return augment(sessionId, userMessage, toMessages(context), statusSink::tryEmitNext);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doFinally(signal -> statusSink.tryEmitComplete());

        Flux<ChatStreamEvent> model = prepared.flatMapMany(messages -> chatClient.prompt()
                .messages(messages)
                .stream()
                .chatResponse()
                .concatMap(AbstractChatFeatureService::toEvents));

        // 모델 이벤트는 prepared 완료 후에만 시작되므로 STATUS → 모델 순서가 자연히 보장된다.
        return Flux.merge(statusSink.asFlux(), model);
    }

    private static Flux<ChatStreamEvent> toEvents(ChatResponse response) {
        Generation result = response.getResult();
        if (result == null || result.getOutput() == null) {
            return Flux.empty();
        }
        AssistantMessage output = result.getOutput();
        List<ChatStreamEvent> events = new ArrayList<>(2);
        Object thinking = output.getMetadata().get(THINKING_METADATA_KEY);
        if (thinking != null && !thinking.toString().isEmpty()) {
            events.add(ChatStreamEvent.thinking(thinking.toString()));
        }
        String text = output.getText();
        if (text != null && !text.isEmpty()) {
            events.add(ChatStreamEvent.token(text));
        }
        return Flux.fromIterable(events);
    }

    /**
     * 프롬프트 전송 직전, 서브클래스가 메시지 리스트를 증강할 수 있는 훅. 기본은 무변경.
     * 예: {@link DocQaService}가 벡터 검색 결과를 SystemMessage로 맨 앞에 주입한다
     * (docs/06-doc-rag-design.md 6.3절).
     *
     * @param eventEmitter 부가 이벤트 발행 콜백 — STATUS(진행 상태 문구)·SOURCES(참조
     *                     문서 목록) 등을 발행하면 실시간으로 클라이언트에 전달된다.
     *                     시간이 걸리는 작업의 앞뒤로 STATUS를 발행하라
     *                     (docs/07-thinking-tool-status-design.md 4절).
     */
    protected List<Message> augment(String sessionId, String userMessage, List<Message> messages,
                                    Consumer<ChatStreamEvent> eventEmitter) {
        return messages;
    }

    private static List<Message> toMessages(ConversationContext context) {
        List<Message> messages = new ArrayList<>();
        context.summary().ifPresent(s ->
                messages.add(new SystemMessage("Summary of the earlier conversation:\n" + s)));
        for (ChatMessageDto dto : context.recentMessages()) {
            messages.add("user".equals(dto.role())
                    ? new UserMessage(dto.content())
                    : new AssistantMessage(dto.content()));
        }
        return messages;
    }
}
