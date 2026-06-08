package org.example.snow.ai.application;

import org.example.snow.ai.domain.QuizType;
import org.springframework.util.StringUtils;

public record QuizGenerationCommand(
        String scopeText,
        String quizType,
        int quizCount
) {

    private static final int MIN_QUIZ_COUNT = 1;
    private static final int MAX_QUIZ_COUNT = 20;

    public QuizGenerationCommand {
        // 허용값(객관식/단답형/서술형) 검증 후 정규화된 label로 보관 (벗어나면 INVALID_QUIZ_TYPE)
        quizType = QuizType.from(quizType).label();
        if (quizCount < MIN_QUIZ_COUNT || quizCount > MAX_QUIZ_COUNT) {
            throw new IllegalArgumentException("문제 개수는 1개 이상 20개 이하로 요청할 수 있습니다.");
        }

        scopeText = StringUtils.hasText(scopeText) ? scopeText.trim() : "노트북 전체 핵심 내용";
    }
}
