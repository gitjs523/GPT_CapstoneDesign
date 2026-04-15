package org.example.snow.ai.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.example.snow.ai.application.AnswerGenerationCommand;

import java.util.List;

public record GenerateAnswerRequest(
        @NotBlank(message = "질문은 필수입니다.")
        String question,

        @NotEmpty(message = "검색된 문서 조각은 최소 1개 이상 필요합니다.")
        @Valid
        List<RetrievedSectionRequest> sections
) {

    public AnswerGenerationCommand toCommand() {
        return new AnswerGenerationCommand(
                question,
                sections.stream()
                        .map(RetrievedSectionRequest::toRetrievedSection)
                        .toList()
        );
    }
}
