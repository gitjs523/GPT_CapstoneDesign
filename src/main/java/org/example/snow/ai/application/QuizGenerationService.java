package org.example.snow.ai.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.snow.ai.domain.GeneratedQuiz;
import org.example.snow.ai.domain.GenerationContext;
import org.example.snow.ai.domain.GenerationJob;
import org.example.snow.ai.infra.GeneratedQuizRepository;
import org.example.snow.ai.infra.GenerationContextRepository;
import org.example.snow.ai.infra.GenerationJobRepository;
import org.example.snow.document.domain.Section;
import org.example.snow.document.infra.SectionRepository;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

    private final GenerationJobRepository generationJobRepository;
    private final SectionRepository sectionRepository;
    private final GenerationContextRepository generationContextRepository;
    private final GeneratedQuizRepository generatedQuizRepository;
    private final EmbeddingSearchService embeddingSearchService;
    private final OllamaService ollamaService;

    @Async
    @Transactional
    public void runAsync(Long jobId) {
        GenerationJob job = generationJobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GENERATION_JOB_NOT_FOUND));
        try {
            run(job);
        } catch (Exception e) {
            log.error("Quiz generation failed. jobId={}", jobId, e);
            markFailed(jobId);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long jobId) {
        generationJobRepository.findById(jobId).ifPresent(job -> {
            job.fail(0);
            generationJobRepository.save(job);
        });
    }

    private void run(GenerationJob job) {
        job.start();

        ResolvedPromptTemplate resolvedPromptTemplate = ResolvedPromptTemplate.from(job.getPromptTemplate());

        List<RetrievedSection> retrievedSections = embeddingSearchService.searchSimilarSections(
                job.getNotebook().getNotebookId(),
                job.getScopeText(),
                Math.max(8, job.getQuizCount())
        );

        if (retrievedSections.isEmpty()) {
            job.fail(0);
            return;
        }

        List<Long> retrievedSectionIds = parseSectionIds(retrievedSections);
        Map<Long, Section> sectionsById = sectionRepository.findAllById(retrievedSectionIds).stream()
                .collect(Collectors.toMap(Section::getSectionId, Function.identity()));
        saveGenerationContexts(job, retrievedSections, sectionsById);

        QuizGenerationCommand command = new QuizGenerationCommand(
                job.getScopeText(),
                job.getQuizType(),
                job.getDifficulty(),
                job.getQuizCount()
        );
        List<GeneratedQuiz> savedQuizzes = generateAndSaveQuizzes(job, command, retrievedSections, retrievedSectionIds, resolvedPromptTemplate);
        updateJobStatus(job, job.getQuizCount(), savedQuizzes.size());
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
            } catch (RuntimeException e) {
                log.warn("Single quiz generation failed. jobId={}, quizOrder={}", job.getJobId(), quizOrder, e);
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
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
