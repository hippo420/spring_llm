package app.ai.rag;

import app.ai.chat.dto.AttachmentDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 문서 인제스천 파이프라인 (docs/doc-rag-design.md 5절):
 * 업로드 파일 → Tika 파싱 → 토큰 청크 분할 → 메타데이터 부착 → pgvector 저장
 * (임베딩은 {@link VectorStore#add}가 내부에서 bge-m3로 수행).
 *
 * <p>동기 처리다 — bge-m3는 소형 모델이라 수십 페이지 문서도 업로드 요청 안에서 끝난다.
 * 파싱/임베딩 실패는 {@link RagDocumentEntity.Status#FAILED}로 기록하고 에러를 던진다.
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    /** 청크 메타데이터 키 — 검색 필터({@code sessionId})·삭제({@code documentId})·출처 표기({@code filename})의 기준. */
    public static final String META_DOCUMENT_ID = "documentId";
    public static final String META_SESSION_ID = "sessionId";
    public static final String META_FILENAME = "filename";
    public static final String META_CHUNK_INDEX = "chunkIndex";

    private final VectorStore vectorStore;
    private final RagDocumentRepository ragDocumentRepository;
    // 기본값(청크 800토큰, 최소 350자)으로 시작 — 검색 품질에 따라 조정 (설계 문서 5절)
    private final TokenTextSplitter splitter = TokenTextSplitter.builder().build();

    public DocumentIngestionService(VectorStore vectorStore, RagDocumentRepository ragDocumentRepository) {
        this.vectorStore = vectorStore;
        this.ragDocumentRepository = ragDocumentRepository;
    }

    public AttachmentDto ingest(String sessionId, MultipartFile file) {
        UUID documentId = UUID.randomUUID();
        UUID sessionUuid = UUID.fromString(sessionId);
        String filename = safeFilename(file);

        try {
            String text = parse(file, filename);
            if (text.isBlank()) {
                recordFailure(documentId, sessionUuid, filename, file);
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "문서에서 텍스트를 추출하지 못했습니다 (스캔 이미지 PDF 등). 텍스트가 포함된 파일을 올려주세요.");
            }

            List<Document> chunks = split(documentId, sessionId, filename, text);
            vectorStore.add(chunks);

            RagDocumentEntity saved = ragDocumentRepository.save(new RagDocumentEntity(
                    documentId, sessionUuid, filename, file.getContentType(),
                    file.getSize(), chunks.size(), RagDocumentEntity.Status.READY));
            log.info("문서 인덱싱 완료 (session={}, doc={}, file={}, chunks={})",
                    sessionId, documentId, filename, chunks.size());
            return toDto(saved);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("문서 인덱싱 실패 (session={}, file={})", sessionId, filename, e);
            recordFailure(documentId, sessionUuid, filename, file);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "문서 인덱싱에 실패했습니다: " + e.getMessage());
        }
    }

    /** 세션의 문서 목록 (READY/FAILED 모두 — 실패도 사용자에게 보인다). */
    public List<AttachmentDto> findDocuments(String sessionId) {
        return ragDocumentRepository.findBySessionIdOrderByCreatedAtAsc(UUID.fromString(sessionId))
                .stream()
                .map(DocumentIngestionService::toDto)
                .toList();
    }

    /**
     * 세션 삭제 시 벡터 청크 + 문서 원장을 함께 지운다. 벡터 삭제가 실패해도 예외를
     * 전파하지 않는다 — 고아 청크는 sessionId 메타데이터가 남아 있어 세션 검색(다른
     * sessionId 필터)에도, 공용 코퍼스 검색(sessionId 보유 청크 배제)에도 걸리지 않는다(무해).
     */
    @Transactional
    public void deleteSessionDocuments(String sessionId) {
        try {
            vectorStore.delete(new FilterExpressionBuilder()
                    .eq(META_SESSION_ID, sessionId)
                    .build());
        } catch (Exception e) {
            log.warn("세션 벡터 청크 삭제 실패 — 고아 청크는 세션/공용 검색 모두에서 배제되므로 무해 (session={}): {}",
                    sessionId, e.getMessage());
        }
        ragDocumentRepository.deleteBySessionId(UUID.fromString(sessionId));
    }

    private String parse(MultipartFile file, String filename) throws IOException {
        // Tika는 내용 기반으로 포맷을 감지하지만, 파일명이 있으면 감지 정확도가 올라간다.
        Resource resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        return new TikaDocumentReader(resource).get().stream()
                .map(Document::getText)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"))
                .strip();
    }

    private List<Document> split(UUID documentId, String sessionId, String filename, String text) {
        Document source = Document.builder()
                .text(text)
                .metadata(Map.of(
                        META_DOCUMENT_ID, documentId.toString(),
                        META_SESSION_ID, sessionId,
                        META_FILENAME, filename))
                .build();
        List<Document> chunks = splitter.apply(List.of(source));
        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).getMetadata().put(META_CHUNK_INDEX, i);
        }
        return chunks;
    }

    private void recordFailure(UUID documentId, UUID sessionUuid, String filename, MultipartFile file) {
        try {
            ragDocumentRepository.save(new RagDocumentEntity(
                    documentId, sessionUuid, filename, file.getContentType(),
                    file.getSize(), 0, RagDocumentEntity.Status.FAILED));
        } catch (Exception e) {
            log.warn("FAILED 문서 기록 실패 (doc={}): {}", documentId, e.getMessage());
        }
    }

    private static String safeFilename(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return "unnamed";
        }
        // 일부 브라우저는 경로째 보낸다 — 파일명만 남기고, 컬럼 길이(255)에 맞춘다.
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        return name.length() <= 255 ? name : name.substring(name.length() - 255);
    }

    private static AttachmentDto toDto(RagDocumentEntity entity) {
        return new AttachmentDto(entity.id().toString(), entity.filename(), entity.sizeBytes(),
                entity.chunkCount(), entity.status().name());
    }
}
