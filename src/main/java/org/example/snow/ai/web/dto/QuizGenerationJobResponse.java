package org.example.snow.ai.web.dto;

import org.example.snow.ai.application.QuizGenerationJobResult;
import org.example.snow.ai.domain.GenerationJobStatus;

import java.time.LocalDateTime;
import java.util.List;

public record QuizGenerationJobResponse(
        Long jobId,
        Long notebookId,
        String scopeText,
        String quizType,
        String difficulty,
        Integer quizCount,
        Integer resultCount,
        GenerationJobStatus status,
        String modelName,
        Long promptTemplateId,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        List<GeneratedQuizResponse> quizzes
) {

    public static QuizGenerationJobResponse from(QuizGenerationJobResult result) {
        return new QuizGenerationJobResponse(
                result.jobId(),
                result.notebookId(),
                result.scopeText(),
                result.quizType(),
                result.difficulty(),
                result.quizCount(),
                result.resultCount(),
                result.status(),
                result.modelName(),
                result.promptTemplateId(),
                result.createdAt(),
                result.startedAt(),
                result.finishedAt(),
                result.quizzes().stream()
                        .map(GeneratedQuizResponse::from)
                        .toList()
        );
    }
}
