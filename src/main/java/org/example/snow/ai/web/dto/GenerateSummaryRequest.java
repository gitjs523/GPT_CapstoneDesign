package org.example.snow.ai.web.dto;

import jakarta.validation.constraints.NotBlank;

public record GenerateSummaryRequest(
        @NotBlank(message = "문서 내용은 필수입니다.")
        String content
) {
}