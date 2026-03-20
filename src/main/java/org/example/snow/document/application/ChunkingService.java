package org.example.snow.document.application;

import org.example.snow.document.domain.ChunkStrategy;
import org.example.snow.document.domain.DocumentChunk;
import org.example.snow.document.domain.ExtractedDocument;
import org.example.snow.document.domain.ExtractedSection;
import org.example.snow.document.domain.SourceUnitType;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkingService {

    public ChunkStrategy resolveStrategy(SourceUnitType sourceType, ChunkStrategy requestedStrategy) {
        ChunkStrategy strategy = requestedStrategy == null ? ChunkStrategy.AUTO : requestedStrategy;

        if (strategy == ChunkStrategy.AUTO) {
            return switch (sourceType) {
                case PAGE -> ChunkStrategy.PAGE;
                case SLIDE -> ChunkStrategy.SLIDE;
                case DOCUMENT -> ChunkStrategy.PARAGRAPH;
            };
        }

        if (strategy == ChunkStrategy.PAGE && sourceType != SourceUnitType.PAGE) {
            throw new BusinessException(ErrorCode.PAGE_CHUNK_STRATEGY_NOT_ALLOWED);
        }

        if (strategy == ChunkStrategy.SLIDE && sourceType != SourceUnitType.SLIDE) {
            throw new BusinessException(ErrorCode.SLIDE_CHUNK_STRATEGY_NOT_ALLOWED);
        }

        return strategy;
    }

    public List<DocumentChunk> chunk(ExtractedDocument document, ChunkStrategy requestedStrategy) {
        ChunkStrategy appliedStrategy = resolveStrategy(document.sourceType(), requestedStrategy);

        return switch (appliedStrategy) {
            case PAGE, SLIDE -> toSectionChunks(document);
            case PARAGRAPH -> toParagraphChunks(document);
            case AUTO -> throw new IllegalStateException("AUTO strategy should have been resolved before chunking.");
        };
    }

    private List<DocumentChunk> toSectionChunks(ExtractedDocument document) {
        List<DocumentChunk> chunks = new ArrayList<>();
        int order = 1;

        for (ExtractedSection section : document.sections()) {
            if (section.text().isBlank()) {
                continue;
            }
            chunks.add(new DocumentChunk(order++, document.sourceType(), section.index(), section.heading(), section.text()));
        }

        return chunks;
    }

    private List<DocumentChunk> toParagraphChunks(ExtractedDocument document) {
        List<DocumentChunk> chunks = new ArrayList<>();
        int order = 1;

        for (ExtractedSection section : document.sections()) {
            String[] paragraphs = section.text().split("\\n{2,}");
            int paragraphIndex = 1;
            for (String paragraph : paragraphs) {
                String normalizedParagraph = paragraph.trim();
                if (normalizedParagraph.isBlank()) {
                    continue;
                }
                String heading = section.heading() + " - Paragraph " + paragraphIndex++;
                chunks.add(new DocumentChunk(order++, document.sourceType(), section.index(), heading, normalizedParagraph));
            }
        }

        return chunks;
    }
}
