package org.example.snow.ai.application;

import org.example.snow.ai.domain.QuizQaHistory;

import java.time.LocalDateTime;

public record QuizQaHistoryResult(
        Long qaHistoryId,
        String question,
        String answer,
        boolean answerable,
        LocalDateTime createdAt
) {

    public static QuizQaHistoryResult from(QuizQaHistory history) {
        return new QuizQaHistoryResult(
                history.getQaHistoryId(),
                history.getUserQuestion(),
                history.getAiAnswer(),
                history.isAnswerable(),
                history.getCreatedAt()
        );
    }
}
