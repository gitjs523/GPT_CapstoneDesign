package org.example.snow.ai.application;

import lombok.RequiredArgsConstructor;
import org.example.snow.ai.domain.GeneratedQuiz;
import org.example.snow.ai.domain.GenerationJob;
import org.example.snow.ai.domain.PromptTemplate;
import org.example.snow.ai.infra.GeneratedQuizRepository;
import org.example.snow.ai.infra.GenerationJobRepository;
import org.example.snow.ai.infra.PromptTemplateRepository;
import org.example.snow.document.application.DocumentService;
import org.example.snow.document.domain.Section;
import org.example.snow.document.infra.SectionRepository;
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
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuizService {

    private final NotebookRepository notebookRepository;
    private final PromptTemplateRepository promptTemplateRepository;
    private final GenerationJobRepository generationJobRepository;
    private final GeneratedQuizRepository generatedQuizRepository;
    private final QuizGenerationService quizGenerationService;
    private final DocumentService documentService;
    private final SectionRepository sectionRepository;

    @Value("${spring.ai.ollama.chat.options.model:unknown}")
    private String chatModelName;

    @Transactional
    public QuizGenerationJobResult requestGeneration(Long userId, Long notebookId, QuizGenerationCommand command) {
        Notebook notebook = getNotebookWithOwnershipCheck(userId, notebookId);
        documentService.validateNoneAnalyzing(notebookId);
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
    public List<QuizGenerationJobResult> getJobs(Long userId, Long notebookId) {
        getNotebookWithOwnershipCheck(userId, notebookId);
        return generationJobRepository.findAllByNotebook_NotebookIdAndDeletedAtIsNullOrderByCreatedAtDesc(notebookId)
                .stream()
                .map(job -> QuizGenerationJobResult.from(job, List.of()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GeneratedQuizResult> getQuizzes(Long userId, Long jobId) {
        getJobWithOwnershipCheck(userId, jobId);
        return generatedQuizRepository.findAllByGenerationJob_JobIdAndDeletedAtIsNullOrderByQuizOrderAsc(jobId)
                .stream()
                .map(GeneratedQuizResult::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public GeneratedQuizResult getQuiz(Long userId, Long quizId) {
        GeneratedQuiz quiz = getQuizWithOwnershipCheck(userId, quizId);
        return GeneratedQuizResult.from(quiz);
    }

    @Transactional(readOnly = true)
    public List<Section> getQuizSourceSections(Long userId, Long quizId) {
        GeneratedQuiz quiz = getQuizWithOwnershipCheck(userId, quizId);
        List<Long> sourceSectionIds = quiz.getSourceSectionIds();

        if (sourceSectionIds == null) {
            throw new BusinessException(ErrorCode.QUIZ_SOURCE_UNAVAILABLE);
        }

        Map<Long, Section> sectionsById = sectionRepository
                .findAllBySectionIdInAndDeletedAtIsNull(sourceSectionIds)
                .stream()
                .collect(Collectors.toMap(Section::getSectionId, Function.identity()));

        return sourceSectionIds.stream()
                .distinct()
                .map(sectionsById::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private GeneratedQuiz getQuizWithOwnershipCheck(Long userId, Long quizId) {
        GeneratedQuiz quiz = generatedQuizRepository.findByQuizIdAndDeletedAtIsNull(quizId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND));
        if (!quiz.getGenerationJob().getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.QUIZ_ACCESS_DENIED);
        }
        return quiz;
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
