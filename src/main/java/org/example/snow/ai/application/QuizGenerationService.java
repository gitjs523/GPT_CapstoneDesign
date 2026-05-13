package org.example.snow.ai.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.snow.ai.domain.GeneratedQuiz;
import org.example.snow.ai.domain.GenerationContext;
import org.example.snow.ai.domain.GenerationJob;
import org.example.snow.ai.domain.PromptTemplate;
import org.example.snow.ai.infra.GeneratedQuizRepository;
import org.example.snow.ai.infra.GenerationContextRepository;
import org.example.snow.ai.infra.GenerationJobRepository;
import org.example.snow.ai.infra.PromptTemplateRepository;
import org.example.snow.document.domain.Section;
import org.example.snow.document.infra.SectionRepository;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.example.snow.notebook.domain.Notebook;
import org.example.snow.notebook.infra.NotebookRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizGenerationService {

    private static final int RETRIEVAL_LIMIT = 8;

    private final NotebookRepository notebookRepository;
    private final SectionRepository sectionRepository;
    private final PromptTemplateRepository promptTemplateRepository;
    private final GenerationJobRepository generationJobRepository;
    private final GenerationContextRepository generationContextRepository;
    private final GeneratedQuizRepository generatedQuizRepository;
    private final EmbeddingSearchService embeddingSearchService;
    private final OllamaService ollamaService;

    @Value("${spring.ai.ollama.chat.options.model:unknown}")
    private String chatModelName;

    @Transactional
    public QuizGenerationJobResult generate(Long userId, Long notebookId, QuizGenerationCommand command) {
        Notebook notebook = getNotebookWithOwnershipCheck(userId, notebookId);
        PromptTemplate promptTemplate = promptTemplateRepository.findFirstByIsActiveTrueOrderByCreatedAtDesc()
                .orElse(null);
        ResolvedPromptTemplate resolvedPromptTemplate = ResolvedPromptTemplate.from(promptTemplate);

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
        job.start();

        List<GeneratedQuiz> savedQuizzes;
        try {
            List<RetrievedSection> retrievedSections = embeddingSearchService.searchSimilarSections(
                    notebookId,
                    command.scopeText(),
                    RETRIEVAL_LIMIT
            );
            if (retrievedSections.isEmpty()) {
                job.fail(0);
                return buildResult(job);
            }

            List<Long> retrievedSectionIds = parseSectionIds(retrievedSections);
            Map<Long, Section> sectionsById = sectionRepository.findAllById(retrievedSectionIds).stream()
                    .collect(Collectors.toMap(Section::getSectionId, Function.identity()));
            saveGenerationContexts(job, retrievedSections, sectionsById);

            savedQuizzes = generateAndSaveQuizzes(job, command, retrievedSections, retrievedSectionIds, resolvedPromptTemplate);
            updateJobStatus(job, command.quizCount(), savedQuizzes.size());
        } catch (RuntimeException exception) {
            log.error("Quiz generation failed. jobId={}", job.getJobId(), exception);
            job.fail(0);
            return buildResult(job);
        }

        return QuizGenerationJobResult.from(
                job,
                savedQuizzes.stream()
                        .map(GeneratedQuizResult::from)
                        .toList()
        );
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

    private List<GeneratedQuiz> generateAndSaveQuizzes(
            GenerationJob job,
            QuizGenerationCommand command,
            List<RetrievedSection> retrievedSections,
            List<Long> retrievedSectionIds,
            ResolvedPromptTemplate promptTemplate
    ) {
        List<GeneratedQuiz> savedQuizzes = new java.util.ArrayList<>();
        for (int quizOrder = 1; quizOrder <= command.quizCount(); quizOrder++) {
            try {
                GeneratedQuizDraft draft = ollamaService.generateQuiz(new QuizGenerationPrompt(
                        command.scopeText(),
                        command.quizType(),
                        command.difficulty(),
                        quizOrder,
                        retrievedSections,
                        promptTemplate
                ));

                List<Long> sourceSectionIds = resolveSourceSectionIds(draft.sourceSectionIds(), retrievedSectionIds);
                GeneratedQuiz quiz = generatedQuizRepository.save(GeneratedQuiz.create(
                        job,
                        quizOrder,
                        StringUtils.hasText(draft.quizType()) ? draft.quizType() : command.quizType(),
                        draft.questionText(),
                        draft.choices(),
                        draft.answer(),
                        draft.explanation(),
                        sourceSectionIds
                ));
                savedQuizzes.add(quiz);
            } catch (RuntimeException exception) {
                log.warn("Single quiz generation failed. jobId={}, quizOrder={}", job.getJobId(), quizOrder, exception);
            }
        }
        return savedQuizzes;
    }

    private void saveGenerationContexts(
            GenerationJob job,
            List<RetrievedSection> retrievedSections,
            Map<Long, Section> sectionsById
    ) {
        List<GenerationContext> contexts = retrievedSections.stream()
                .map(retrievedSection -> {
                    Long sectionId = parseSectionId(retrievedSection.sectionId());
                    Section section = sectionId == null ? null : sectionsById.get(sectionId);
                    if (section == null) {
                        return null;
                    }
                    return GenerationContext.create(
                            job,
                            section,
                            retrievedSection.rank(),
                            retrievedSection.similarityScore()
                    );
                })
                .filter(Objects::nonNull)
                .toList();
        generationContextRepository.saveAll(contexts);
    }

    private void updateJobStatus(GenerationJob job, int requestedCount, int savedCount) {
        if (savedCount == requestedCount) {
            job.complete(savedCount);
        } else if (savedCount > 0) {
            job.partialComplete(savedCount);
        } else {
            job.fail(0);
        }
    }

    private QuizGenerationJobResult buildResult(GenerationJob job) {
        List<GeneratedQuizResult> quizzes = generatedQuizRepository
                .findAllByGenerationJob_JobIdAndDeletedAtIsNullOrderByQuizOrderAsc(job.getJobId())
                .stream()
                .map(GeneratedQuizResult::from)
                .toList();
        return QuizGenerationJobResult.from(job, quizzes);
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

    private List<Long> parseSectionIds(List<RetrievedSection> retrievedSections) {
        return retrievedSections.stream()
                .map(RetrievedSection::sectionId)
                .map(this::parseSectionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<Long> resolveSourceSectionIds(List<Long> generatedSourceIds, List<Long> retrievedSectionIds) {
        Set<Long> retrievedIdSet = Set.copyOf(retrievedSectionIds);
        List<Long> validGeneratedIds = generatedSourceIds.stream()
                .filter(retrievedIdSet::contains)
                .distinct()
                .toList();
        if (!validGeneratedIds.isEmpty()) {
            return validGeneratedIds;
        }
        return List.copyOf(new LinkedHashSet<>(retrievedSectionIds));
    }

    private Long parseSectionId(String sectionId) {
        if (!StringUtils.hasText(sectionId)) {
            return null;
        }
        try {
            return Long.valueOf(sectionId.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
