package org.example.snow.document.domain;

import java.util.List;

public record Chunk(
        int order,
        String heading,
        String text,
        SourceUnitType sourceType,
        int sourceStartIndex,
        int sourceEndIndex,
        List<Integer> sourceIndices
) {

    public Chunk {
        sourceIndices = List.copyOf(sourceIndices);
    }
}
