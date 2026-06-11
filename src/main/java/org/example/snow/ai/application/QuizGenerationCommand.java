package org.example.snow.ai.application;

import org.example.snow.ai.domain.QuizType;
import org.springframework.util.StringUtils;

public record QuizGenerationCommand(
        String scopeText,
        String quizType,
        int quizCount
) {

    private static final int MIN_QUIZ_COUNT = 1;
    private static final int MAX_QUIZ_COUNT = 10;

    public QuizGenerationCommand {
        // 허용값(객관식/단답형/서술형) 검증 후 정규화된 label로 보관 (벗어나면 INVALID_QUIZ_TYPE)
        quizType = QuizType.from(quizType).label();
        if (quizCount < MIN_QUIZ_COUNT || quizCount > MAX_QUIZ_COUNT) {
            throw new IllegalArgumentException("문제 개수는 1개 이상 10개 이하로 요청할 수 있습니다.");
        }

        // 출제 범위는 필수다. 빈값을 임의 기본값으로 대체하면 사용자가 입력하지 않은 scope가
        // generation_job·검색 질의·프롬프트에 그대로 박히므로, DTO @NotBlank와 함께 여기서도 거부한다.
        if (!StringUtils.hasText(scopeText)) {
            throw new IllegalArgumentException("출제 분야 또는 범위는 필수입니다.");
        }
        scopeText = scopeText.trim();
    }
}
