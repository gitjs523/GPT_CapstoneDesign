package org.example.snow.ai.web.dto;

import jakarta.validation.constraints.NotBlank;
import org.example.snow.ai.application.RetrievedSection;

public record RetrievedSectionRequest(
        @NotBlank(message = "sectionId는 필수입니다.")
        String sectionId,

        String heading,

        @NotBlank(message = "검색된 문서 본문은 필수입니다.")
        String text,

        String documentName,
        Integer sourceStartIndex,
        Integer sourceEndIndex,
        Integer rank,
        Double similarityScore
) {

    public RetrievedSection toRetrievedSection() {
        return new RetrievedSection(
                sectionId,
                heading,
                text,
                documentName,
                sourceStartIndex,
                sourceEndIndex,
                rank,
                similarityScore
        );
    }
}
