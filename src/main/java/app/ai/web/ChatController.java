package app.ai.web;

import app.ai.chat.dto.AttachmentDto;
import app.ai.chat.history.ChatHistoryService;
import app.ai.chat.history.ChatSessionTitleGenerator;
import app.ai.chat.dto.ChatMessageDto;
import app.ai.chat.dto.ChatMessageRequest;
import app.ai.chat.dto.ChatSessionSummary;
import app.ai.chat.feature.BrainstormService;
import app.ai.chat.feature.ChatFeature;
import app.ai.chat.feature.ChatFeatureService;
import app.ai.chat.feature.CodeExplainService;
import app.ai.chat.feature.CodeReviewService;
import app.ai.chat.feature.DocQaService;
import app.ai.chat.feature.EmailWriteService;
import app.ai.chat.feature.GrammarCheckService;
import app.ai.chat.feature.SentimentAnalysisService;
import app.ai.chat.feature.TranslateService;
import app.ai.chat.feature.history.HistoryChatService;
import app.ai.chat.feature.normal.GeneralChatService;
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
    private final ChatSessionTitleGenerator titleGenerator;
    private final GeneralChatService generalChatService;
    private final HistoryChatService historyChatService;
    private final TranslateService translateService;
    private final CodeExplainService codeExplainService;
    private final CodeReviewService codeReviewService;
    private final GrammarCheckService grammarCheckService;
    private final EmailWriteService emailWriteService;
    private final BrainstormService brainstormService;
    private final DocQaService docQaService;
    private final SentimentAnalysisService sentimentAnalysisService;

    public ChatController(ChatHistoryService chatHistoryService,
                          ChatSessionTitleGenerator titleGenerator,
                          GeneralChatService generalChatService,
                          HistoryChatService historyChatService,
                          TranslateService translateService,
                          CodeExplainService codeExplainService,
                          CodeReviewService codeReviewService,
                          GrammarCheckService grammarCheckService,
                          EmailWriteService emailWriteService,
                          BrainstormService brainstormService,
                          DocQaService docQaService,
                          SentimentAnalysisService sentimentAnalysisService) {
        this.chatHistoryService = chatHistoryService;
        this.titleGenerator = titleGenerator;
        this.generalChatService = generalChatService;
        this.historyChatService = historyChatService;
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
        // TODO: 파싱(Tika/PDF 리더) 후 RAG용으로 pgvector에 임베딩. 지금은 업로드만 지원.
        return new AttachmentDto(UUID.randomUUID().toString(), file.getOriginalFilename(), file.getSize());
    }
}
