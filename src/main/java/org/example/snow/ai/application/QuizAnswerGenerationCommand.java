package org.example.snow.ai.application;

import org.example.snow.ai.domain.GeneratedQuiz;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

public record QuizAnswerGenerationCommand(
        String question,
        GeneratedQuiz quiz,
        List<RetrievedSection> sourceSections
) {

    public QuizAnswerGenerationCommand {
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("질문은 필수입니다.");
        }
        if (quiz == null) {
            throw new IllegalArgumentException("퀴즈는 필수입니다.");
        }

        question = question.trim();
        sourceSections = sourceSections == null ? List.of() : List.copyOf(sourceSections);

        if (sourceSections.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("근거 섹션에는 null이 포함될 수 없습니다.");
        }
    }
}
