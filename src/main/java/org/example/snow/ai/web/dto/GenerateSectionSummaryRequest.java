package org.example.snow.ai.web.dto;

import jakarta.validation.constraints.NotBlank;

public record GenerateSectionSummaryRequest(
        @NotBlank(message = "주제는 필수입니다.")
        String topic,

        @NotBlank(message = "문서 내용은 필수입니다.")
        String content
) {
}