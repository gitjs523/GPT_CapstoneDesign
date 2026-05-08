package org.example.snow.ai.application;

import org.springframework.util.StringUtils;

import java.util.List;

public record GeneratedAnswer(
        String answer,
        List<String> citedSectionIds,
        boolean answerable
) {

    public GeneratedAnswer {
        if (!StringUtils.hasText(answer)) {
            throw new IllegalArgumentException("생성된 답변은 비어 있을 수 없습니다.");
        }

        answer = answer.trim();
        citedSectionIds = citedSectionIds == null ? List.of() : List.copyOf(citedSectionIds);
    }
}
