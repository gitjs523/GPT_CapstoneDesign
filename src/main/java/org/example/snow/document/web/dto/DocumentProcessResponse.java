package org.example.snow.document.web.dto;

import org.example.snow.document.application.DocumentProcessingResult;
import org.example.snow.document.domain.ChunkStrategy;

import java.util.List;

public record DocumentProcessResponse(
        String originalFilename,
        String contentType,
        ChunkStrategy appliedChunkStrategy,
        int extractedSectionCount,
        int chunkCount,
        int totalCharacters,
        String preprocessedText,
        List<DocumentChunkResponse> chunks
) {

    public static DocumentProcessResponse from(DocumentProcessingResult result) {
        List<DocumentChunkResponse> chunkResponses = result.chunks().stream()
                .map(DocumentChunkResponse::from)
                .toList();

        return new DocumentProcessResponse(
                result.originalFilename(),
                result.contentType(),
                result.appliedChunkStrategy(),
                result.extractedSectionCount(),
                result.chunkCount(),
                result.totalCharacters(),
                result.preprocessedText(),
                chunkResponses
        );
    }
}
