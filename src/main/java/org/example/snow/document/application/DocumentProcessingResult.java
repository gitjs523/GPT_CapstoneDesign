package org.example.snow.document.application;

import org.example.snow.document.domain.ChunkStrategy;
import org.example.snow.document.domain.Chunk;

import java.util.List;

public record DocumentProcessingResult(
        String originalFilename,
        String contentType,
        ChunkStrategy appliedChunkStrategy,
        int sourceUnitCount,
        int sectionCount,
        int chunkCount,
        int totalCharacters,
        String preprocessedText,
        List<Chunk> chunks
) {
}
