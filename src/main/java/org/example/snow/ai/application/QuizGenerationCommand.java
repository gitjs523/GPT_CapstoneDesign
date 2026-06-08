package org.example.snow.ai.application;

import org.springframework.util.StringUtils;

public record QuizGenerationCommand(
        String scopeText,
        String quizType,
        int quizCount
) {

    private static final int MIN_QUIZ_COUNT = 1;
    private static final int MAX_QUIZ_COUNT = 20;

    public QuizGenerationCommand {
        if (!StringUtils.hasText(quizType)) {
            throw new IllegalArgumentException("문제 유형은 필수입니다.");
        }
        if (quizCount < MIN_QUIZ_COUNT || quizCount > MAX_QUIZ_COUNT) {
            throw new IllegalArgumentException("문제 개수는 1개 이상 20개 이하로 요청할 수 있습니다.");
        }

        scopeText = StringUtils.hasText(scopeText) ? scopeText.trim() : "노트북 전체 핵심 내용";
        quizType = quizType.trim();
    }
}
