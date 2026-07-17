package app.ai.web;

import app.ai.chat.dto.AttachmentDto;
import app.ai.chat.history.ChatHistoryService;
import app.ai.chat.history.ChatSessionTitleGenerator;
import app.ai.chat.dto.ChatMessageDto;
import app.ai.chat.dto.ChatMessageRequest;
import app.ai.chat.dto.ChatSessionSummary;
import app.ai.chat.feature.ChatFeature;
import app.ai.chat.feature.ChatFeatureService;
import app.ai.chat.feature.DocQaService;
import app.ai.chat.feature.history.HistoryChatService;
import app.ai.chat.feature.normal.GeneralChatService;
import app.ai.rag.DocumentIngestionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatHistoryService chatHistoryService;
    private final ChatSessionTitleGenerator titleGenerator;
    private final GeneralChatService generalChatService;
    private final HistoryChatService historyChatService;
    private final DocQaService docQaService;
    private final DocumentIngestionService documentIngestionService;

    public ChatController(ChatHistoryService chatHistoryService,
                          ChatSessionTitleGenerator titleGenerator,
                          GeneralChatService generalChatService,
                          HistoryChatService historyChatService,
                          DocQaService docQaService,
                          DocumentIngestionService documentIngestionService) {
        this.chatHistoryService = chatHistoryService;
        this.titleGenerator = titleGenerator;
        this.generalChatService = generalChatService;
        this.historyChatService = historyChatService;
        this.docQaService = docQaService;
        this.documentIngestionService = documentIngestionService;
    }

    @GetMapping("/features")
    public List<ChatFeatureOption> features() {
        return List.of(ChatFeature.values()).stream()
                .map(f -> new ChatFeatureOption(f.name(), f.label()))
                .toList();
    }

    public record ChatFeatureOption(String value, String label) {
    }

    @GetMapping("/sessions")
    public List<ChatSessionSummary> sessions() {
        return chatHistoryService.findSessions();
    }

    @PostMapping("/sessions")
    public ChatSessionSummary createSession() {
        return chatHistoryService.createSession();
    }

    @DeleteMapping("/sessions/{sessionId}")
    public void deleteSession(@PathVariable String sessionId) {
        // 벡터 청크는 FK가 없으므로 히스토리 삭제(rag_document CASCADE) 전에 명시적으로 지운다.
        documentIngestionService.deleteSessionDocuments(sessionId);
        chatHistoryService.deleteSession(sessionId);
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public List<ChatMessageDto> messages(@PathVariable String sessionId) {
        return chatHistoryService.findMessages(sessionId);
    }

    @PostMapping(value = "/sessions/{sessionId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamReply(@PathVariable String sessionId, @RequestBody ChatMessageRequest request) {
        ChatFeature feature = request.featureOrDefault();

        // GENERAL_CHAT은 영속성 없이 처리한다: 히스토리에 저장/조회하지 않고
        // 현재 메시지 하나로만 단발 응답을 스트리밍한다.
        if (feature == ChatFeature.GENERAL_CHAT) {
            return generalChatService.stream(request.content());
        }

        // stream()보다 먼저 실행되어야 한다: ChatFeatureService 계약상 세션 컨텍스트가
        // 이미 현재 사용자 메시지로 끝나 있어야 하기 때문.
        long userSeq = chatHistoryService.appendMessage(sessionId, "user", request.content());

        ChatFeatureService service = resolveService(feature);

        StringBuilder fullReply = new StringBuilder();
        return service.stream(sessionId, request.content())
                .doOnNext(fullReply::append)
                .doOnComplete(() -> {
                    chatHistoryService.appendMessage(sessionId, "assistant", fullReply.toString());
                    // 세션의 첫 턴이 끝났으면 대화 내용을 요약해 제목을 만든다 (비동기).
                    if (userSeq == 1) {
                        titleGenerator.generateAsync(sessionId, request.content(), fullReply.toString());
                    }
                });
    }

    private ChatFeatureService resolveService(ChatFeature feature) {
        return switch (feature) {
            case GENERAL_CHAT -> generalChatService;
            case HISTORY -> historyChatService;
            case DOC_RAG -> docQaService;
        };
    }

    /** 문서 업로드 + 인제스천 (파싱 → 청크 → 임베딩 → pgvector). 문서는 이 세션에서만 검색된다. */
    @PostMapping("/sessions/{sessionId}/attachments")
    public AttachmentDto uploadAttachment(@PathVariable String sessionId,
                                          @RequestParam("file") MultipartFile file) {
        return documentIngestionService.ingest(sessionId, file);
    }

    @GetMapping("/sessions/{sessionId}/attachments")
    public List<AttachmentDto> attachments(@PathVariable String sessionId) {
        return documentIngestionService.findDocuments(sessionId);
    }
}
