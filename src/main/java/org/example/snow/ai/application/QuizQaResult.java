package org.example.snow.ai.application;

public record QuizQaResult(
        Long qaHistoryId,
        String answer,
        boolean answerable
) {
}
