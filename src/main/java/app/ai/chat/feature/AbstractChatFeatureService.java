package app.ai.chat.feature;

import app.ai.chat.history.ChatHistoryService;
import app.ai.chat.dto.ChatMessageDto;
import app.ai.chat.dto.ConversationContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * 영속성(대화 히스토리) 계층의 공통 베이스: {@link ChatClient} 플루언트 API 기반
 * (서비스마다 프로토타입 스코프의 {@link ChatClient.Builder}를 받으므로 기능별로 고유한
 * 기본 시스템 프롬프트를 유지한다). 영속성 없는 단발 계층은
 * {@link app.ai.chat.feature.normal.GeneralChatService},
 * 저수준 {@code ChatModel}/{@code Prompt}/{@code Message} API와의 대응 관계는
 * docs/spring-ai-chatmodel-vs-chatclient.md 참고.
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

    @Override
    public Flux<String> stream(String sessionId, String userMessage) {
        ConversationContext context = chatHistoryService.getContext(sessionId);
        return chatClient.prompt()
                .messages(toMessages(context))
                .stream()
                .content();
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
