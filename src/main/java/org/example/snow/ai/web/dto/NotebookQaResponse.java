package org.example.snow.ai.web.dto;

import org.example.snow.ai.application.NotebookQaResult;

import java.util.List;

public record NotebookQaResponse(
        Long qaHistoryId,
        String answer,
        boolean answerable,
        List<Long> citedSectionIds
) {

    public static NotebookQaResponse from(NotebookQaResult result) {
        return new NotebookQaResponse(
                result.qaHistoryId(),
                result.answer(),
                result.answerable(),
                result.citedSectionIds()
        );
    }
}
