package org.example.snow.ai.application;

import org.example.snow.ai.domain.NotebookQaHistory;

import java.time.LocalDateTime;
import java.util.List;

public record NotebookQaHistoryResult(
        Long qaHistoryId,
        String question,
        String answer,
        boolean answerable,
        List<Long> citedSectionIds,
        LocalDateTime createdAt
) {

    public NotebookQaHistoryResult {
        citedSectionIds = citedSectionIds == null ? List.of() : List.copyOf(citedSectionIds);
    }

    public static NotebookQaHistoryResult from(NotebookQaHistory history) {
        return new NotebookQaHistoryResult(
                history.getQaHistoryId(),
                history.getUserQuestion(),
                history.getAiAnswer(),
                history.isAnswerable(),
                history.getCitedSectionIds(),
                history.getCreatedAt()
        );
    }
}
