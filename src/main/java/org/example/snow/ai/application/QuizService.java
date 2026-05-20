package org.example.snow.ai.application;

import lombok.RequiredArgsConstructor;
import org.example.snow.ai.domain.GeneratedQuiz;
import org.example.snow.ai.domain.GenerationJob;
import org.example.snow.ai.domain.PromptTemplate;
import org.example.snow.ai.infra.GeneratedQuizRepository;
import org.example.snow.ai.infra.GenerationJobRepository;
import org.example.snow.ai.infra.PromptTemplateRepository;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.example.snow.notebook.domain.Notebook;
import org.example.snow.notebook.infra.NotebookRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QuizService {

    private final NotebookRepository notebookRepository;
    private final PromptTemplateRepository promptTemplateRepository;
    private final GenerationJobRepository generationJobRepository;
    private final GeneratedQuizRepository generatedQuizRepository;
    private final QuizGenerationService quizGenerationService;

    @Value("${spring.ai.ollama.chat.options.model:unknown}")
    private String chatModelName;

    @Transactional
    public QuizGenerationJobResult requestGeneration(Long userId, Long notebookId, QuizGenerationCommand command) {
        Notebook notebook = getNotebookWithOwnershipCheck(userId, notebookId);
        PromptTemplate promptTemplate = promptTemplateRepository.findFirstByIsActiveTrueOrderByCreatedAtDesc()
                .orElse(null);

        GenerationJob job = generationJobRepository.save(GenerationJob.create(
                notebook.getUser(),
                notebook,
                promptTemplate,
                command.scopeText(),
                command.quizType(),
                command.difficulty(),
                command.quizCount(),
                chatModelName
        ));
        Long jobId = job.getJobId();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                quizGenerationService.runAsync(jobId);
            }
        });

        return buildResult(job);
    }

    @Transactional(readOnly = true)
    public QuizGenerationJobResult getJob(Long userId, Long jobId) {
        GenerationJob job = getJobWithOwnershipCheck(userId, jobId);
        return buildResult(job);
    }

    @Transactional(readOnly = true)
    public List<GeneratedQuizResult> getQuizzes(Long userId, Long jobId) {
        getJobWithOwnershipCheck(userId, jobId);
        return generatedQuizRepository.findAllByGenerationJob_JobIdAndDeletedAtIsNullOrderByQuizOrderAsc(jobId)
                .stream()
                .map(GeneratedQuizResult::from)
                .toList();
    }

    private Notebook getNotebookWithOwnershipCheck(Long userId, Long notebookId) {
        Notebook notebook = notebookRepository.findByNotebookIdAndDeletedAtIsNull(notebookId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTEBOOK_NOT_FOUND));
        if (!notebook.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOTEBOOK_ACCESS_DENIED);
        }
        return notebook;
    }

    private GenerationJob getJobWithOwnershipCheck(Long userId, Long jobId) {
        GenerationJob job = generationJobRepository.findByJobIdAndDeletedAtIsNull(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GENERATION_JOB_NOT_FOUND));
        if (!job.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return job;
    }

    private QuizGenerationJobResult buildResult(GenerationJob job) {
        List<GeneratedQuizResult> quizzes = generatedQuizRepository
                .findAllByGenerationJob_JobIdAndDeletedAtIsNullOrderByQuizOrderAsc(job.getJobId())
                .stream()
                .map(GeneratedQuizResult::from)
                .toList();
        return QuizGenerationJobResult.from(job, quizzes);
    }
}
