package org.example.snow.ai.web.dto;

import org.example.snow.ai.application.NotebookQaHistoryResult;

import java.time.LocalDateTime;
import java.util.List;

public record NotebookQaHistoryResponse(
        Long qaHistoryId,
        String question,
        String answer,
        boolean answerable,
        List<Long> citedSectionIds,
        LocalDateTime createdAt
) {

    public static NotebookQaHistoryResponse from(NotebookQaHistoryResult result) {
        return new NotebookQaHistoryResponse(
                result.qaHistoryId(),
                result.question(),
                result.answer(),
                result.answerable(),
                result.citedSectionIds(),
                result.createdAt()
        );
    }
}
