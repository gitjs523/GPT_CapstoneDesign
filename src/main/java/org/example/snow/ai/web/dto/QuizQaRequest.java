package org.example.snow.ai.web.dto;

import jakarta.validation.constraints.NotBlank;

public record QuizQaRequest(
        @NotBlank(message = "질문은 필수입니다.")
        String question
) {
}
