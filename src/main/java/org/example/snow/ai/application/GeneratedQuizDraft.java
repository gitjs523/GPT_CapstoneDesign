package org.example.snow.ai.application;

import org.springframework.util.StringUtils;

import java.util.List;

public record GeneratedQuizDraft(
        String quizType,
        String questionText,
        String choices,
        String answer,
        String explanation,
        List<Long> sourceSectionIds
) {

    public GeneratedQuizDraft {
        if (!StringUtils.hasText(questionText)) {
            throw new IllegalArgumentException("생성된 문제 본문은 비어 있을 수 없습니다.");
        }
        if (!StringUtils.hasText(answer)) {
            throw new IllegalArgumentException("생성된 문제 정답은 비어 있을 수 없습니다.");
        }

        quizType = StringUtils.hasText(quizType) ? quizType.trim() : null;
        questionText = questionText.trim();
        choices = StringUtils.hasText(choices) ? choices.trim() : "";
        answer = answer.trim();
        explanation = StringUtils.hasText(explanation) ? explanation.trim() : "";
        sourceSectionIds = sourceSectionIds == null ? List.of() : List.copyOf(sourceSectionIds);
    }
}
