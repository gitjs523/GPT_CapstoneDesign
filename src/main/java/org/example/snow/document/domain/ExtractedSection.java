package org.example.snow.document.domain;

import java.util.List;

public record ExtractedSection(
        int order,
        String heading,
        String text,
        SourceUnitType sourceType,
        int sourceStartIndex,
        int sourceEndIndex,
        List<Integer> sourceIndices
) {
    public ExtractedSection {
        sourceIndices = List.copyOf(sourceIndices);
    }
}
