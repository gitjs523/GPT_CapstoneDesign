package org.example.snow.ai.application;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

public record AnswerGenerationCommand(
        String question,
        List<RetrievedSection> sections
) {

    public AnswerGenerationCommand {
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("질문은 필수입니다.");
        }
        if (sections == null || sections.isEmpty()) {
            throw new IllegalArgumentException("검색된 문서 조각은 최소 1개 이상 필요합니다.");
        }

        question = question.trim();
        sections = List.copyOf(sections);

        if (sections.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("검색된 문서 조각에는 null이 포함될 수 없습니다.");
        }
    }
}
