package org.example.snow.notebook.web.dto;

import org.example.snow.notebook.domain.Notebook;

import java.time.LocalDateTime;

public record NotebookResponse(
        Long notebookId,
        String title,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static NotebookResponse from(Notebook notebook) {
        return new NotebookResponse(
                notebook.getNotebookId(),
                notebook.getTitle(),
                notebook.getCreatedAt(),
                notebook.getUpdatedAt()
        );
    }
}
