package org.example.snow.document.domain;

public record ExtractedSection(
        int index,
        String heading,
        String text
) {
}
