package org.example.snow.document.web.dto;

import org.example.snow.document.domain.AnalysisStatus;
import org.example.snow.document.domain.Document;

import java.time.LocalDateTime;

public record DocumentResponse(
        Long documentId,
        Long notebookId,
        String originalFileName,
        String fileType,
        Long fileSize,
        Integer pageCount,
        AnalysisStatus analysisStatus,
        LocalDateTime uploadedAt
) {
    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getDocumentId(),
                document.getNotebook().getNotebookId(),
                document.getOriginalFileName(),
                document.getFileType(),
                document.getFileSize(),
                document.getPageCount(),
                document.getAnalysisStatus(),
                document.getUploadedAt()
        );
    }
}
