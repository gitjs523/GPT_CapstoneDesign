package org.example.snow.document.domain;

public record DocumentChunk(
        int order,
        SourceUnitType sourceType,
        int sourceIndex,
        String heading,
        String text
) {
}
