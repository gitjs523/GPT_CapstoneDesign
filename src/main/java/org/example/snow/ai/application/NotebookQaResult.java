package org.example.snow.ai.application;

import java.util.List;

public record NotebookQaResult(
        Long qaHistoryId,
        String answer,
        boolean answerable,
        List<Long> citedSectionIds
) {

    public NotebookQaResult {
        citedSectionIds = citedSectionIds == null ? List.of() : List.copyOf(citedSectionIds);
    }
}
