package org.example.snow.document.web.dto;

import org.example.snow.document.domain.ExtractedChunk;
import org.example.snow.document.domain.SourceUnitType;

import java.util.List;

public record ChunkResponse(
        int order,
        String heading,
        String text,
        SourceUnitType sourceType,
        int sourceStartIndex,
        int sourceEndIndex,
        List<Integer> sourceIndices
) {

    public static ChunkResponse from(ExtractedChunk chunk) {
        return new ChunkResponse(
                chunk.order(),
                chunk.heading(),
                chunk.text(),
                chunk.sourceType(),
                chunk.sourceStartIndex(),
                chunk.sourceEndIndex(),
                chunk.sourceIndices()
        );
    }
}
