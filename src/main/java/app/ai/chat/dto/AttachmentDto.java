package app.ai.chat.dto;

/** 업로드 문서 요약 — status는 READY(검색 가능) | FAILED(파싱/임베딩 실패). */
public record AttachmentDto(String id, String filename, long size, int chunkCount, String status) {
}
