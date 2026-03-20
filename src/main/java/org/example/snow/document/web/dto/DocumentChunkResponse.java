package org.example.snow.document.web.dto;

import org.example.snow.document.domain.DocumentChunk;
import org.example.snow.document.domain.SourceUnitType;

public record DocumentChunkResponse(
        int order,
        SourceUnitType sourceType,
        int sourceIndex,
        String heading,
        String text
) {

    public static DocumentChunkResponse from(DocumentChunk chunk) {
        return new DocumentChunkResponse(
                chunk.order(),
                chunk.sourceType(),
                chunk.sourceIndex(),
                chunk.heading(),
                chunk.text()
        );
    }
}
