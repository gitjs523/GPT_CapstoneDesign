package org.example.snow.ai.web.dto;

import org.example.snow.ai.application.GeneratedAnswer;

import java.util.List;

public record GeneratedAnswerResponse(
        String answer,
        List<String> citedSectionIds,
        boolean answerable
) {

    public static GeneratedAnswerResponse from(GeneratedAnswer generatedAnswer) {
        return new GeneratedAnswerResponse(
                generatedAnswer.answer(),
                generatedAnswer.citedSectionIds(),
                generatedAnswer.answerable()
        );
    }
}
