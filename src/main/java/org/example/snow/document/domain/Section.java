package org.example.snow.document.domain;

import java.util.List;

public record Section(
        int order,
        String heading,
        String text,
        SourceUnitType sourceType,
        int sourceStartIndex,
        int sourceEndIndex,
        List<Integer> sourceIndices
) {

    public Section {
        sourceIndices = List.copyOf(sourceIndices);
    }
}
