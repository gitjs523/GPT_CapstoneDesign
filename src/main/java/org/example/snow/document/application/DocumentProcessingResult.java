package org.example.snow.document.application;

import org.example.snow.document.domain.ExtractedChunk;
import org.example.snow.document.domain.ExtractedDocument;
import org.example.snow.document.domain.ExtractedSection;

import java.util.List;

public record DocumentProcessingResult(
        String originalFilename,
        String contentType,
        String docTitle,
        int sourceUnitCount,
        int sectionCount,
        int chunkCount,
        int totalCharacters,
        String preprocessedText,
        ExtractedDocument extractedDocument,
        List<ExtractedSection> sections,
        List<ExtractedChunk> chunks
) {
}
