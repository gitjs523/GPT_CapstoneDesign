package org.example.snow.ai.application;

import org.example.snow.ai.domain.GeneratedQuiz;

import java.time.LocalDateTime;
import java.util.List;

public record GeneratedQuizResult(
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

    public GeneratedQuizResult {
        sourceSectionIds = sourceSectionIds == null ? List.of() : List.copyOf(sourceSectionIds);
    }

    public static GeneratedQuizResult from(GeneratedQuiz quiz) {
        return new GeneratedQuizResult(
                quiz.getQuizId(),
                quiz.getGenerationJob().getJobId(),
                quiz.getQuizOrder(),
                quiz.getQuizType(),
                quiz.getQuestionText(),
                quiz.getChoices(),
                quiz.getAnswer(),
                quiz.getExplanation(),
                quiz.getSourceSectionIds(),
                quiz.getCreatedAt()
        );
    }
}
