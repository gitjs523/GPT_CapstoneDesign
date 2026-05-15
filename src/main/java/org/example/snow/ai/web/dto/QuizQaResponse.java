package org.example.snow.ai.web.dto;

import org.example.snow.ai.application.QuizQaResult;

public record QuizQaResponse(
        Long qaHistoryId,
        String answer,
        boolean answerable
) {

    public static QuizQaResponse from(QuizQaResult result) {
        return new QuizQaResponse(
                result.qaHistoryId(),
                result.answer(),
                result.answerable()
        );
    }
}
