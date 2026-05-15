package org.example.snow.ai.web.dto;

import org.example.snow.ai.application.QuizQaHistoryResult;

import java.time.LocalDateTime;

public record QuizQaHistoryResponse(
        Long qaHistoryId,
        String question,
        String answer,
        boolean answerable,
        LocalDateTime createdAt
) {

    public static QuizQaHistoryResponse from(QuizQaHistoryResult result) {
        return new QuizQaHistoryResponse(
                result.qaHistoryId(),
                result.question(),
                result.answer(),
                result.answerable(),
                result.createdAt()
        );
    }
}
