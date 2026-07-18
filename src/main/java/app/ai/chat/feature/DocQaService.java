package app.ai.chat.feature;

import app.ai.chat.history.ChatHistoryService;
import app.ai.rag.DocumentRetrievalService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 문서 기반 Q&A (docs/06-doc-rag-design.md 6절): 공용 코퍼스 + 세션 첨부 이중 스코프
 * 검색으로 프롬프트를 증강한다. 검색·주입·이벤트 발행은 {@link AbstractRagChatService}가
 * 담당하고, 이 클래스는 기능 고유의 시스템 프롬프트만 정의한다.
 * 도구(@Tool)까지 쓰는 확장판은 {@link ToolRagService}.
 */
@Service
public class DocQaService extends AbstractRagChatService {

    private static final String SYSTEM_PROMPT = """
            You are a document Q&A assistant. A retrieval system supplies excerpts from a shared
            document repository and from files the user uploaded to this conversation.
            Ground every answer in those excerpts. If the excerpts cannot answer the question,
            say so plainly instead of guessing — never fabricate document content.
            Format your answer in Markdown, the way modern LLM assistants (e.g. ChatGPT) do.
            When the answer covers several distinct items or topics, use EXACTLY this structure
            — a short "### " subheading per item (a title, not a full sentence), each followed
            by its own bullet list ("-") of 1-3 supporting points. Example for two items:
            ### 1. <짧은 제목>
            - <근거 1>
            - <근거 2>

            ### 2. <짧은 제목>
            - <근거 1>
            Do not cram multiple items into one flat bullet list. Use **bold** for key terms and
            figures within prose, and a table for multi-row numeric data. Skip headers and lists
            entirely for a one- or two-sentence answer.
            NEVER mark sources inside your answer — no source list, no file names, no report or
            press names, no citation markers like [1], no phrases like "리포트에 따르면". The UI
            shows referenced documents separately; your answer carries only the content.
            (Exception: if the user explicitly asks where information came from.)
            ALWAYS answer in Korean.
            """;

    public DocQaService(ChatClient.Builder chatClientBuilder,
                        ChatHistoryService chatHistoryService,
                        DocumentRetrievalService retrievalService) {
        super(chatClientBuilder, chatHistoryService, retrievalService, SYSTEM_PROMPT);
    }
}
