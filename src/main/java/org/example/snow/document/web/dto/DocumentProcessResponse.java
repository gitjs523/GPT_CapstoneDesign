package org.example.snow.document.web.dto;

import org.example.snow.document.application.DocumentProcessingResult;
import org.example.snow.document.domain.ChunkStrategy;

import java.util.List;

public record DocumentProcessResponse(
        String originalFilename,
        String contentType,
        ChunkStrategy appliedChunkStrategy,
        int sourceUnitCount,
        int sectionCount,
        int chunkCount,
        int totalCharacters,
        String preprocessedText,
        List<ChunkResponse> chunks
) {

    public static DocumentProcessResponse from(DocumentProcessingResult result) {
        List<ChunkResponse> chunkResponses = result.chunks().stream()
                .map(ChunkResponse::from)
                .toList();

        return new DocumentProcessResponse(
                result.originalFilename(),
                result.contentType(),
                result.appliedChunkStrategy(),
                result.sourceUnitCount(),
                result.sectionCount(),
                result.chunkCount(),
                result.totalCharacters(),
                result.preprocessedText(),
                chunkResponses
        );
    }
}
