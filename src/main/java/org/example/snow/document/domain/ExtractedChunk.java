package org.example.snow.document.domain;

import java.util.List;

public record ExtractedChunk(
        int order,
        String heading,
        String text,
        SourceUnitType sourceType,
        int sourceStartIndex,
        int sourceEndIndex,
        List<Integer> sourceIndices
) {
    public ExtractedChunk {
        sourceIndices = List.copyOf(sourceIndices);
    }
}
