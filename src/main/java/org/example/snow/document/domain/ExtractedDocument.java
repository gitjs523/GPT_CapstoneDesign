package org.example.snow.document.domain;

import java.util.List;

public record ExtractedDocument(
        String originalFilename,
        String contentType,
        SourceUnitType sourceType,
        List<SourceUnit> sourceUnits
) {

    public ExtractedDocument withSourceUnits(List<SourceUnit> sourceUnits) {
        return new ExtractedDocument(originalFilename, contentType, sourceType, sourceUnits);
    }
}
