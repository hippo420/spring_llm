package app.ai.web;

import app.ai.chat.AttachmentDto;
import app.ai.chat.ChatHistoryService;
import app.ai.chat.ChatMessageDto;
import app.ai.chat.ChatMessageRequest;
import app.ai.chat.ChatSessionSummary;
import app.ai.chat.feature.BrainstormService;
import app.ai.chat.feature.ChatFeature;
import app.ai.chat.feature.ChatFeatureService;
import app.ai.chat.feature.CodeExplainService;
import app.ai.chat.feature.CodeReviewService;
import app.ai.chat.feature.DocQaService;
import app.ai.chat.feature.EmailWriteService;
import app.ai.chat.feature.GeneralChatService;
import app.ai.chat.feature.GrammarCheckService;
import app.ai.chat.feature.SentimentAnalysisService;
import app.ai.chat.feature.SummarizeService;
import app.ai.chat.feature.TranslateService;
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
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatHistoryService chatHistoryService;
    private final GeneralChatService generalChatService;
    private final SummarizeService summarizeService;
    private final TranslateService translateService;
    private final CodeExplainService codeExplainService;
    private final CodeReviewService codeReviewService;
    private final GrammarCheckService grammarCheckService;
    private final EmailWriteService emailWriteService;
    private final BrainstormService brainstormService;
    private final DocQaService docQaService;
    private final SentimentAnalysisService sentimentAnalysisService;

    public ChatController(ChatHistoryService chatHistoryService,
                           GeneralChatService generalChatService,
                           SummarizeService summarizeService,
                           TranslateService translateService,
                           CodeExplainService codeExplainService,
                           CodeReviewService codeReviewService,
                           GrammarCheckService grammarCheckService,
                           EmailWriteService emailWriteService,
                           BrainstormService brainstormService,
                           DocQaService docQaService,
                           SentimentAnalysisService sentimentAnalysisService) {
        this.chatHistoryService = chatHistoryService;
        this.generalChatService = generalChatService;
        this.summarizeService = summarizeService;
        this.translateService = translateService;
        this.codeExplainService = codeExplainService;
        this.codeReviewService = codeReviewService;
        this.grammarCheckService = grammarCheckService;
        this.emailWriteService = emailWriteService;
        this.brainstormService = brainstormService;
        this.docQaService = docQaService;
        this.sentimentAnalysisService = sentimentAnalysisService;
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
        chatHistoryService.deleteSession(sessionId);
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public List<ChatMessageDto> messages(@PathVariable String sessionId) {
        return chatHistoryService.findMessages(sessionId);
    }

    @PostMapping(value = "/sessions/{sessionId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamReply(@PathVariable String sessionId, @RequestBody ChatMessageRequest request) {
        // Must precede stream(): the ChatFeatureService contract expects the session
        // context to already end with the current user message.
        chatHistoryService.appendMessage(sessionId, "user", request.content());

        ChatFeatureService service = resolveService(request.featureOrDefault());

        StringBuilder fullReply = new StringBuilder();
        return service.stream(sessionId, request.content())
                .doOnNext(fullReply::append)
                .doOnComplete(() -> chatHistoryService.appendMessage(sessionId, "assistant", fullReply.toString()));
    }

    private ChatFeatureService resolveService(ChatFeature feature) {
        return switch (feature) {
            case GENERAL_CHAT -> generalChatService;
            case SUMMARIZE -> summarizeService;
            case TRANSLATE -> translateService;
            case CODE_EXPLAIN -> codeExplainService;
            case CODE_REVIEW -> codeReviewService;
            case GRAMMAR_CHECK -> grammarCheckService;
            case EMAIL_WRITE -> emailWriteService;
            case BRAINSTORM -> brainstormService;
            case DOC_QA -> docQaService;
            case SENTIMENT_ANALYSIS -> sentimentAnalysisService;
        };
    }

    @PostMapping("/attachments")
    public AttachmentDto uploadAttachment(@RequestParam("file") MultipartFile file) {
        // TODO: parse (Tika/PDF reader) and embed into pgvector for RAG. Upload-only for now.
        return new AttachmentDto(UUID.randomUUID().toString(), file.getOriginalFilename(), file.getSize());
    }
}
