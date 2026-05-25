package org.example.snow.ai.web.dto;

import org.example.snow.ai.application.QuizSourceResult;

public record QuizSourceResponse(Long sectionId, Long sourceDocumentId, String sourceDocumentName) {

    public static QuizSourceResponse from(QuizSourceResult result) {
        return new QuizSourceResponse(result.sectionId(), result.sourceDocumentId(), result.sourceDocumentName());
    }
}
