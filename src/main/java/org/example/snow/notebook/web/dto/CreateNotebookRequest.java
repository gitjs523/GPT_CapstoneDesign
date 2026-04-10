package org.example.snow.notebook.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateNotebookRequest(
        @NotBlank(message = "노트북 제목은 필수입니다.")
        @Size(max = 255, message = "노트북 제목은 255자 이하여야 합니다.")
        String title
) {
}
