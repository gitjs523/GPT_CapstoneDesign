package org.example.snow.ai.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.example.snow.ai.application.QuizGenerationCommand;

public record QuizGenerationRequest(
        String scopeText,

        @NotBlank(message = "문제 유형은 필수입니다.")
        String quizType,

        @NotBlank(message = "난이도는 필수입니다.")
        String difficulty,

        @Min(value = 1, message = "문제 개수는 1개 이상이어야 합니다.")
        @Max(value = 20, message = "문제 개수는 20개 이하이어야 합니다.")
        Integer quizCount
) {

    public QuizGenerationCommand toCommand() {
        return new QuizGenerationCommand(
                scopeText,
                quizType,
                difficulty,
                quizCount == null ? 5 : quizCount
        );
    }
}
