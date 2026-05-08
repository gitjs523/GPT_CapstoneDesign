package org.example.snow.document.domain;

import java.util.List;

public record ExtractedDocument(
        String originalFilename,
        String contentType,
        SourceUnitType sourceType,
        List<ExtractedSourceUnit> sourceUnits
) {

    public ExtractedDocument withSourceUnits(List<ExtractedSourceUnit> sourceUnits) {
        return new ExtractedDocument(originalFilename, contentType, sourceType, sourceUnits);
    }
}
