package org.example.snow.document.application;

import org.example.snow.document.domain.ChunkStrategy;
import org.example.snow.document.domain.DocumentChunk;

import java.util.List;

public record DocumentProcessingResult(
        String originalFilename,
        String contentType,
        ChunkStrategy appliedChunkStrategy,
        int extractedSectionCount,
        int chunkCount,
        int totalCharacters,
        String preprocessedText,
        List<DocumentChunk> chunks
) {
}
