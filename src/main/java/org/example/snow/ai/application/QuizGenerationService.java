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
import org.springframework.stereotype.Service;
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

    private static final String NO_RETRIEVAL_FAILURE_REASON =
            "문제 생성을 위한 문서 근거를 찾지 못했습니다. 문서 분석/임베딩 완료 여부 또는 scopeText를 확인하세요.";

    private final GenerationJobRepository generationJobRepository;
    private final SectionRepository sectionRepository;
    private final GenerationContextRepository generationContextRepository;
    private final GeneratedQuizRepository generatedQuizRepository;
    private final EmbeddingSearchService embeddingSearchService;
    private final OllamaService ollamaService;
    private final GenerationJobStatusManager statusManager;

    public void run(Long jobId) {
        try {
            statusManager.markRunning(jobId);
            GenerationJob job = generationJobRepository.findByIdWithDetails(jobId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.GENERATION_JOB_NOT_FOUND));
            execute(job);
        } catch (RuntimeException exception) {
            log.error("Quiz generation failed. jobId={}", jobId, exception);
            statusManager.markFailed(
                    jobId,
                    "퀴즈 생성 처리 중 오류가 발생했습니다: " + extractFailureMessage(exception)
            );
        }
    }

    private void execute(GenerationJob job) {
        ResolvedPromptTemplate resolvedPromptTemplate = ResolvedPromptTemplate.from(job.getPromptTemplate());

        List<RetrievedSection> retrievedSections = embeddingSearchService.searchSimilarSections(
                job.getNotebook().getNotebookId(),
                job.getScopeText(),
                Math.max(8, job.getQuizCount())
        );

        if (retrievedSections.isEmpty()) {
            statusManager.markFailed(job.getJobId(), NO_RETRIEVAL_FAILURE_REASON);
            return;
        }

        List<Long> retrievedSectionIds = parseSectionIds(retrievedSections);
        Map<Long, Section> sectionsById = sectionRepository.findAllById(retrievedSectionIds).stream()
                .collect(Collectors.toMap(Section::getSectionId, Function.identity()));
        saveGenerationContexts(job, retrievedSections, sectionsById);

        QuizGenerationCommand command = new QuizGenerationCommand(
                job.getScopeText(),
                job.getQuizType(),
                job.getQuizCount()
        );
        QuizGenerationAttemptResult attemptResult = generateAndSaveQuizzes(
                job,
                command,
                retrievedSections,
                retrievedSectionIds,
                resolvedPromptTemplate
        );
        updateJobStatus(
                job.getJobId(),
                job.getQuizCount(),
                attemptResult.savedQuizzes().size(),
                attemptResult.failureReasons()
        );
    }

    private QuizGenerationAttemptResult generateAndSaveQuizzes(
            GenerationJob job,
            QuizGenerationCommand command,
            List<RetrievedSection> retrievedSections,
            List<Long> retrievedSectionIds,
            ResolvedPromptTemplate promptTemplate
    ) {
        List<GeneratedQuiz> savedQuizzes = new java.util.ArrayList<>();
        List<String> failureReasons = new java.util.ArrayList<>();
        for (int quizOrder = 1; quizOrder <= command.quizCount(); quizOrder++) {
            try {
                GeneratedQuizDraft draft = ollamaService.generateQuiz(new QuizGenerationPrompt(
                        command.scopeText(),
                        command.quizType(),
                        quizOrder,
                        retrievedSections,
                        promptTemplate
                ));

                List<Long> sourceSectionIds = resolveSourceSectionIds(draft.sourceSectionIds(), retrievedSectionIds);
                GeneratedQuiz quiz = generatedQuizRepository.save(GeneratedQuiz.create(
                        job,
                        quizOrder,
                        // quizType은 검증된 요청 입력값이 단일 출처다. 모델이 echo한 quizType("descriptive" 등)은
                        // 신뢰하지 않고 무시한다 (소형 모델이 영어/임의값으로 뱉어 저장값이 어긋나는 것 방지).
                        command.quizType(),
                        draft.questionText(),
                        draft.choices(),
                        draft.answer(),
                        draft.explanation(),
                        sourceSectionIds
                ));
                savedQuizzes.add(quiz);
            } catch (RuntimeException exception) {
                log.warn("Single quiz generation failed. jobId={}, quizOrder={}", job.getJobId(), quizOrder, exception);
                failureReasons.add("quizOrder=" + quizOrder + ": " + extractFailureMessage(exception));
            }
        }
        return new QuizGenerationAttemptResult(savedQuizzes, failureReasons);
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

    private void updateJobStatus(Long jobId, int requestedCount, int savedCount, List<String> failureReasons) {
        if (savedCount == requestedCount) {
            statusManager.markComplete(jobId, savedCount);
        } else if (savedCount > 0) {
            statusManager.markPartialComplete(
                    jobId,
                    savedCount,
                    buildFailureReason("일부 문제 생성에 실패했습니다.", failureReasons)
            );
        } else {
            statusManager.markFailed(
                    jobId,
                    buildFailureReason("문제 생성에 실패했습니다.", failureReasons)
            );
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
        if (generatedSourceIds == null || generatedSourceIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "모델이 sourceSectionIds를 반환하지 않았습니다. validSectionIds=" + retrievedSectionIds
            );
        }

        Set<Long> retrievedIdSet = new LinkedHashSet<>(retrievedSectionIds);
        List<Long> distinctGeneratedIds = generatedSourceIds.stream()
                .distinct()
                .toList();

        List<Long> invalidGeneratedIds = distinctGeneratedIds.stream()
                .filter(sectionId -> !retrievedIdSet.contains(sectionId))
                .toList();
        if (!invalidGeneratedIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "sourceSectionIds에 유효하지 않은 sectionId가 포함되었습니다. invalidSectionIds="
                            + invalidGeneratedIds + ", validSectionIds=" + retrievedSectionIds
            );
        }

        return distinctGeneratedIds;
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

    private String buildFailureReason(String defaultMessage, List<String> failureReasons) {
        if (failureReasons == null || failureReasons.isEmpty()) {
            return defaultMessage;
        }
        return defaultMessage + " " + String.join(" / ", failureReasons);
    }

    private String extractFailureMessage(RuntimeException exception) {
        Throwable current = exception;
        while (current != null) {
            if (StringUtils.hasText(current.getMessage())) {
                return sanitizeFailureMessage(current.getMessage());
            }
            current = current.getCause();
        }
        return "상세 원인을 확인할 수 없습니다.";
    }

    private String sanitizeFailureMessage(String message) {
        String sanitized = message.replaceAll("\\s+", " ").trim();
        if (sanitized.length() > 500) {
            return sanitized.substring(0, 500);
        }
        return sanitized;
    }

    private record QuizGenerationAttemptResult(
            List<GeneratedQuiz> savedQuizzes,
            List<String> failureReasons
    ) {

        private QuizGenerationAttemptResult {
            savedQuizzes = savedQuizzes == null ? List.of() : List.copyOf(savedQuizzes);
            failureReasons = failureReasons == null ? List.of() : List.copyOf(failureReasons);
        }
    }
}
