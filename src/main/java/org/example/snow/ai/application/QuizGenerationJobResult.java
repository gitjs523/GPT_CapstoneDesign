package org.example.snow.ai.application;

import org.example.snow.ai.domain.GenerationJob;
import org.example.snow.ai.domain.GenerationJobStatus;

import java.time.LocalDateTime;
import java.util.List;

public record QuizGenerationJobResult(
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
        List<GeneratedQuizResult> quizzes
) {

    public QuizGenerationJobResult {
        quizzes = quizzes == null ? List.of() : List.copyOf(quizzes);
    }

    public static QuizGenerationJobResult from(GenerationJob job, List<GeneratedQuizResult> quizzes) {
        return new QuizGenerationJobResult(
                job.getJobId(),
                job.getNotebook().getNotebookId(),
                job.getScopeText(),
                job.getQuizType(),
                job.getDifficulty(),
                job.getQuizCount(),
                job.getResultCount(),
                job.getStatus(),
                job.getModelName(),
                job.getPromptTemplate() == null ? null : job.getPromptTemplate().getPromptTemplateId(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getFinishedAt(),
                quizzes
        );
    }
}
