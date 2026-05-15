package org.example.snow.ai.web.dto;

import org.example.snow.ai.application.GeneratedQuizResult;

import java.time.LocalDateTime;
import java.util.List;

public record GeneratedQuizResponse(
        Long quizId,
        Long jobId,
        Integer quizOrder,
        String quizType,
        String questionText,
        String choices,
        String answer,
        String explanation,
        List<Long> sourceSectionIds,
        LocalDateTime createdAt
) {

    public static GeneratedQuizResponse from(GeneratedQuizResult result) {
        return new GeneratedQuizResponse(
                result.quizId(),
                result.jobId(),
                result.quizOrder(),
                result.quizType(),
                result.questionText(),
                result.choices(),
                result.answer(),
                result.explanation(),
                result.sourceSectionIds(),
                result.createdAt()
        );
    }
}
