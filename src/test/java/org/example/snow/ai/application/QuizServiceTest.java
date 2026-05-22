package org.example.snow.ai.application;

import org.example.snow.ai.domain.GeneratedQuiz;
import org.example.snow.ai.domain.GenerationJob;
import org.example.snow.ai.domain.GenerationJobStatus;
import org.example.snow.ai.domain.PromptTemplate;
import org.example.snow.ai.infra.GeneratedQuizRepository;
import org.example.snow.ai.infra.GenerationJobRepository;
import org.example.snow.ai.infra.PromptTemplateRepository;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.example.snow.notebook.domain.Notebook;
import org.example.snow.notebook.infra.NotebookRepository;
import org.example.snow.user.domain.UserAccount;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuizServiceTest {

    private final NotebookRepository notebookRepository = mock(NotebookRepository.class);
    private final PromptTemplateRepository promptTemplateRepository = mock(PromptTemplateRepository.class);
    private final GenerationJobRepository generationJobRepository = mock(GenerationJobRepository.class);
    private final GeneratedQuizRepository generatedQuizRepository = mock(GeneratedQuizRepository.class);
    private final QuizGenerationService quizGenerationService = mock(QuizGenerationService.class);

    private final QuizService quizService = new QuizService(
            notebookRepository,
            promptTemplateRepository,
            generationJobRepository,
            generatedQuizRepository,
            quizGenerationService
    );

    @BeforeEach
    void initTransactionSync() {
        ReflectionTestUtils.setField(quizService, "chatModelName", "qwen3:4b-q4_K_M");
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearTransactionSync() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    // ─────────────────────────── requestGeneration ───────────────────────────

    @Test
    void requestGeneration_createsQueuedJobAndRegistersAsync() {
        Notebook notebook = createNotebook(1L, 10L);
        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));
        when(promptTemplateRepository.findFirstByIsActiveTrueOrderByCreatedAtDesc()).thenReturn(Optional.empty());
        when(generationJobRepository.save(any())).thenAnswer(inv -> {
            GenerationJob job = inv.getArgument(0);
            ReflectionTestUtils.setField(job, "jobId", 55L);
            ReflectionTestUtils.setField(job, "createdAt", LocalDateTime.of(2026, 5, 20, 10, 0));
            return job;
        });
        when(generatedQuizRepository.findAllByGenerationJob_JobIdAndDeletedAtIsNullOrderByQuizOrderAsc(55L))
                .thenReturn(List.of());

        QuizGenerationCommand command = new QuizGenerationCommand("RAG 단원", "MULTIPLE_CHOICE", "중", 3);
        QuizGenerationJobResult result = quizService.requestGeneration(1L, 10L, command);

        assertThat(result.jobId()).isEqualTo(55L);
        assertThat(result.status()).isEqualTo(GenerationJobStatus.QUEUED);
        assertThat(result.quizzes()).isEmpty();

        // afterCommit 트리거 → runAsync 호출 확인
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        verify(quizGenerationService).runAsync(55L);
    }

    @Test
    void requestGeneration_storesPromptTemplateInJob() {
        Notebook notebook = createNotebook(1L, 10L);
        PromptTemplate template = createPromptTemplate();
        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));
        when(promptTemplateRepository.findFirstByIsActiveTrueOrderByCreatedAtDesc()).thenReturn(Optional.of(template));
        when(generationJobRepository.save(any())).thenAnswer(inv -> {
            GenerationJob job = inv.getArgument(0);
            ReflectionTestUtils.setField(job, "jobId", 56L);
            ReflectionTestUtils.setField(job, "createdAt", LocalDateTime.of(2026, 5, 20, 10, 0));
            return job;
        });
        when(generatedQuizRepository.findAllByGenerationJob_JobIdAndDeletedAtIsNullOrderByQuizOrderAsc(56L))
                .thenReturn(List.of());

        quizService.requestGeneration(1L, 10L, new QuizGenerationCommand("단원", "MULTIPLE_CHOICE", "중", 1));

        verify(generationJobRepository).save(any());
    }

    @Test
    void requestGeneration_throwsWhenNotebookNotFound() {
        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> quizService.requestGeneration(
                1L, 10L, new QuizGenerationCommand("단원", "MULTIPLE_CHOICE", "중", 1)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.NOTEBOOK_NOT_FOUND.getMessage());

        verify(generationJobRepository, never()).save(any());
    }

    @Test
    void requestGeneration_throwsWhenNotOwner() {
        Notebook notebook = createNotebook(2L, 10L);
        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));

        assertThatThrownBy(() -> quizService.requestGeneration(
                1L, 10L, new QuizGenerationCommand("단원", "MULTIPLE_CHOICE", "중", 1)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.NOTEBOOK_ACCESS_DENIED.getMessage());

        verify(generationJobRepository, never()).save(any());
    }

    // ──────────────────────────────── getJob ─────────────────────────────────

    @Test
    void getJob_returnsJobWithQuizzes() {
        Notebook notebook = createNotebook(1L, 10L);
        GenerationJob job = createJob(notebook, 55L);
        job.start();
        job.complete(2);
        GeneratedQuiz quiz1 = createQuiz(job, 901L, 1);
        GeneratedQuiz quiz2 = createQuiz(job, 902L, 2);
        when(generationJobRepository.findByJobIdAndDeletedAtIsNull(55L)).thenReturn(Optional.of(job));
        when(generatedQuizRepository.findAllByGenerationJob_JobIdAndDeletedAtIsNullOrderByQuizOrderAsc(55L))
                .thenReturn(List.of(quiz1, quiz2));

        QuizGenerationJobResult result = quizService.getJob(1L, 55L);

        assertThat(result.jobId()).isEqualTo(55L);
        assertThat(result.status()).isEqualTo(GenerationJobStatus.COMPLETED);
        assertThat(result.quizzes()).hasSize(2);
    }

    @Test
    void getJob_throwsWhenJobNotFound() {
        when(generationJobRepository.findByJobIdAndDeletedAtIsNull(55L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> quizService.getJob(1L, 55L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.GENERATION_JOB_NOT_FOUND.getMessage());
    }

    @Test
    void getJob_throwsWhenNotOwner() {
        Notebook notebook = createNotebook(2L, 10L);
        GenerationJob job = createJob(notebook, 55L);
        when(generationJobRepository.findByJobIdAndDeletedAtIsNull(55L)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> quizService.getJob(1L, 55L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.FORBIDDEN.getMessage());
    }

    // ─────────────────────────────── getQuizzes ──────────────────────────────

    @Test
    void getQuizzes_returnsQuizListForJob() {
        Notebook notebook = createNotebook(1L, 10L);
        GenerationJob job = createJob(notebook, 55L);
        GeneratedQuiz quiz1 = createQuiz(job, 901L, 1);
        GeneratedQuiz quiz2 = createQuiz(job, 902L, 2);
        when(generationJobRepository.findByJobIdAndDeletedAtIsNull(55L)).thenReturn(Optional.of(job));
        when(generatedQuizRepository.findAllByGenerationJob_JobIdAndDeletedAtIsNullOrderByQuizOrderAsc(55L))
                .thenReturn(List.of(quiz1, quiz2));

        List<GeneratedQuizResult> result = quizService.getQuizzes(1L, 55L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).quizId()).isEqualTo(901L);
        assertThat(result.get(1).quizId()).isEqualTo(902L);
    }

    @Test
    void getQuizzes_returnsEmptyListWhenNoQuizzesGenerated() {
        Notebook notebook = createNotebook(1L, 10L);
        GenerationJob job = createJob(notebook, 55L);
        when(generationJobRepository.findByJobIdAndDeletedAtIsNull(55L)).thenReturn(Optional.of(job));
        when(generatedQuizRepository.findAllByGenerationJob_JobIdAndDeletedAtIsNullOrderByQuizOrderAsc(55L))
                .thenReturn(List.of());

        List<GeneratedQuizResult> result = quizService.getQuizzes(1L, 55L);

        assertThat(result).isEmpty();
    }

    @Test
    void getQuizzes_throwsWhenNotOwner() {
        Notebook notebook = createNotebook(2L, 10L);
        GenerationJob job = createJob(notebook, 55L);
        when(generationJobRepository.findByJobIdAndDeletedAtIsNull(55L)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> quizService.getQuizzes(1L, 55L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.FORBIDDEN.getMessage());
    }

    // ───────────────────────────── helpers ───────────────────────────────────

    private UserAccount createUser(Long userId) {
        UserAccount user = UserAccount.create("user" + userId + "@example.com", "hash");
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }

    private Notebook createNotebook(Long userId, Long notebookId) {
        Notebook notebook = Notebook.create(createUser(userId), "강의 노트");
        ReflectionTestUtils.setField(notebook, "notebookId", notebookId);
        return notebook;
    }

    private GenerationJob createJob(Notebook notebook, Long jobId) {
        GenerationJob job = GenerationJob.create(
                notebook.getUser(), notebook, null, "RAG 단원", "MULTIPLE_CHOICE", "중", 2, "qwen3:4b"
        );
        ReflectionTestUtils.setField(job, "jobId", jobId);
        ReflectionTestUtils.setField(job, "createdAt", LocalDateTime.of(2026, 5, 20, 10, 0));
        return job;
    }

    private GeneratedQuiz createQuiz(GenerationJob job, Long quizId, int quizOrder) {
        GeneratedQuiz quiz = GeneratedQuiz.create(
                job, quizOrder, "MULTIPLE_CHOICE", "문제 " + quizOrder,
                "[\"보기1\",\"보기2\"]", "보기1", "해설", List.of(100L)
        );
        ReflectionTestUtils.setField(quiz, "quizId", quizId);
        ReflectionTestUtils.setField(quiz, "createdAt", LocalDateTime.of(2026, 5, 20, 10, quizOrder));
        return quiz;
    }

    private PromptTemplate createPromptTemplate() {
        return mock(PromptTemplate.class);
    }
}
