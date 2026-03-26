package org.example.snow.document.web.dto;

import org.example.snow.document.domain.Chunk;
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

    public static ChunkResponse from(Chunk chunk) {
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
