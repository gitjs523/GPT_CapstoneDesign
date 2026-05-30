package org.example.snow.ai.application;

import lombok.RequiredArgsConstructor;
import org.example.snow.ai.domain.GeneratedQuiz;
import org.example.snow.ai.domain.GenerationJob;
import org.example.snow.ai.domain.GenerationJobStatus;
import org.example.snow.ai.domain.PromptTemplate;
import org.example.snow.ai.infra.GeneratedQuizRepository;
import org.example.snow.ai.infra.GenerationJobRepository;
import org.example.snow.ai.infra.PromptTemplateRepository;
import org.example.snow.ai.infra.QuizQaHistoryRepository;
import org.example.snow.document.application.DocumentService;
import org.example.snow.document.domain.Section;
import org.example.snow.document.infra.SectionRepository;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.example.snow.global.queue.ModelQueueService;
import org.example.snow.notebook.domain.Notebook;
import org.example.snow.notebook.infra.NotebookRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class QuizService {

    private final NotebookRepository notebookRepository;
    private final PromptTemplateRepository promptTemplateRepository;
    private final GenerationJobRepository generationJobRepository;
    private final GeneratedQuizRepository generatedQuizRepository;
    private final QuizGenerationService quizGenerationService;
    private final GenerationJobStatusManager statusManager;
    private final DocumentService documentService;
    private final SectionRepository sectionRepository;
    private final ModelQueueService modelQueueService;
    private final QuizQaHistoryRepository quizQaHistoryRepository;

    @Value("${spring.ai.ollama.chat.options.model:unknown}")
    private String chatModelName;

    @Transactional
    public QuizGenerationJobResult requestGeneration(Long userId, Long notebookId, QuizGenerationCommand command) {
        Notebook notebook = getNotebookWithOwnershipCheck(userId, notebookId);
        documentService.validateNoneAnalyzing(notebookId);

        if (!modelQueueService.canAcceptGeneration()) {
            throw new BusinessException(ErrorCode.MODEL_QUEUE_FULL);
        }

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
                boolean submitted = modelQueueService.submitGeneration(
                        () -> quizGenerationService.run(jobId)
                );
                if (!submitted) {
                    // canAcceptGeneration() 통과 후 극히 드문 race condition 대비
                    statusManager.markFailed(jobId);
                }
            }
        });

        return buildResult(job);
    }

    /**
     * 생성 결과(Job)를 soft delete한다.
     * 문제/정답/해설은 모두 generated_quiz 한 엔티티에 저장되므로 개별 quiz가 아닌 job 단위로 삭제한다.
     * 생성된 문제는 모델 성능을 직접 평가할 수 있는 데이터이므로 물리 삭제가 아닌 soft delete로 보존한다.
     *
     * cascade: generated_quiz, quiz_qa_history는 함께 soft delete한다.
     * generation_context(TopK 검색 기록)는 내부 추적/디버깅용이므로 제외한다 (#8/#12 cascade 정책과 동일).
     * 진행 중(QUEUED/RUNNING)인 job은 비동기 생성 task와의 경합을 막기 위해 삭제를 거부한다.
     */
    @Transactional
    public void deleteJob(Long userId, Long jobId) {
        GenerationJob job = getJobWithOwnershipCheck(userId, jobId);
        if (job.getStatus() == GenerationJobStatus.QUEUED || job.getStatus() == GenerationJobStatus.RUNNING) {
            throw new BusinessException(ErrorCode.GENERATION_JOB_IN_PROGRESS);
        }

        LocalDateTime now = LocalDateTime.now();
        quizQaHistoryRepository.softDeleteByJobId(jobId, now);
        generatedQuizRepository.softDeleteByJobId(jobId, now);
        job.softDelete();
        generationJobRepository.save(job);
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
    public List<QuizSourceResult> getQuizSources(Long userId, Long quizId) {
        GeneratedQuiz quiz = getQuizWithOwnershipCheck(userId, quizId);
        List<Long> sourceSectionIds = quiz.getSourceSectionIds();
        if (sourceSectionIds == null) {
            throw new BusinessException(ErrorCode.QUIZ_SOURCE_UNAVAILABLE);
        }

        Map<Long, Integer> orderBySectionId = IntStream.range(0, sourceSectionIds.size())
                .boxed()
                .collect(Collectors.toMap(sourceSectionIds::get, Function.identity(), Math::min));
        Map<Long, Section> sectionsById = sectionRepository
                .findAllBySectionIdInAndDeletedAtIsNull(sourceSectionIds)
                .stream()
                .collect(Collectors.toMap(Section::getSectionId, Function.identity()));

        return sourceSectionIds.stream()
                .distinct()
                .map(sectionsById::get)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt(section -> orderBySectionId.get(section.getSectionId())))
                .map(section -> new QuizSourceResult(
                        section.getSectionId(),
                        section.getDocument().getDocumentId(),
                        section.getDocument().getOriginalFileName()
                ))
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
