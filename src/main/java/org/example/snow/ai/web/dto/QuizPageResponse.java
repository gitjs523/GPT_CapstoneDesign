package org.example.snow.ai.web.dto;

import org.example.snow.ai.application.GeneratedQuizResult;

import java.time.LocalDateTime;

public record QuizPageResponse(
        Long quizId,
        Long jobId,
        Integer quizOrder,
        String quizType,
        String questionText,
        String choices,
        LocalDateTime createdAt
) {

    public static QuizPageResponse from(GeneratedQuizResult result) {
        return new QuizPageResponse(
                result.quizId(),
                result.jobId(),
                result.quizOrder(),
                result.quizType(),
                result.questionText(),
                result.choices(),
                result.createdAt()
        );
    }
}
