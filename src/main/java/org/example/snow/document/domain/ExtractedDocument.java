package org.example.snow.document.domain;

import java.util.List;

public record ExtractedDocument(
        String originalFilename,
        String contentType,
        SourceUnitType sourceType,
        List<ExtractedSection> sections
) {

    public ExtractedDocument withSections(List<ExtractedSection> sections) {
        return new ExtractedDocument(originalFilename, contentType, sourceType, sections);
    }
}
