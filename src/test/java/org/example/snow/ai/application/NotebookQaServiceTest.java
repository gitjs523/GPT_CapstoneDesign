package org.example.snow.ai.application;

import org.example.snow.ai.domain.NotebookQaHistory;
import org.example.snow.ai.infra.NotebookQaHistoryRepository;
import org.example.snow.document.application.DocumentService;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.example.snow.global.queue.ModelQueueService;
import org.example.snow.notebook.domain.Notebook;
import org.example.snow.notebook.infra.NotebookRepository;
import org.example.snow.user.domain.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotebookQaServiceTest {

    private final NotebookRepository notebookRepository = mock(NotebookRepository.class);
    private final NotebookQaHistoryRepository notebookQaHistoryRepository = mock(NotebookQaHistoryRepository.class);
    private final DocumentService documentService = mock(DocumentService.class);
    private final ModelQueueService modelQueueService = mock(ModelQueueService.class);
    private final QaJobStore qaJobStore = mock(QaJobStore.class);
    private final NotebookQaProcessor notebookQaProcessor = mock(NotebookQaProcessor.class);

    private final NotebookQaService notebookQaService = new NotebookQaService(
            notebookRepository,
            notebookQaHistoryRepository,
            documentService,
            modelQueueService,
            qaJobStore,
            notebookQaProcessor
    );

    @BeforeEach
    void setUp() {
        when(modelQueueService.canAcceptGeneration()).thenReturn(true);
        when(modelQueueService.submitGeneration(any())).thenReturn(true);
    }

    // ───────────────────────────── ask ───────────────────────────────────────

    @Test
    void ask_returnsQueuedJobAndSubmitsProcessingToQueue() {
        Notebook notebook = createNotebook(1L, 10L);
        QaJob qaJob = new QaJob("test-uuid", 1L, 10L);
        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));
        when(qaJobStore.create(1L, 10L)).thenReturn(qaJob);

        QaJob result = notebookQaService.ask(1L, 10L, " RAG가 뭐야? ");

        assertThat(result).isSameAs(qaJob);
        assertThat(result.getStatus()).isEqualTo(QaJobStatus.QUEUED);
        verify(qaJobStore).create(1L, 10L);
        verify(modelQueueService).submitGeneration(any());
    }

    @Test
    void ask_throwsWhenQuestionIsBlank() {
        assertThatThrownBy(() -> notebookQaService.ask(1L, 10L, "   "))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVALID_REQUEST.getMessage());

        verify(qaJobStore, never()).create(anyLong(), anyLong());
    }

    @Test
    void ask_throwsWhenQueueFull() {
        Notebook notebook = createNotebook(1L, 10L);
        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));
        when(modelQueueService.canAcceptGeneration()).thenReturn(false);

        assertThatThrownBy(() -> notebookQaService.ask(1L, 10L, "질문"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.MODEL_QUEUE_FULL.getMessage());

        verify(qaJobStore, never()).create(anyLong(), anyLong());
    }

    @Test
    void ask_throwsWhenNotebookNotOwnedByUser() {
        Notebook notebook = createNotebook(2L, 10L);
        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));

        assertThatThrownBy(() -> notebookQaService.ask(1L, 10L, "질문"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("해당 노트북에 접근할 권한이 없습니다.");

        verify(qaJobStore, never()).create(anyLong(), anyLong());
        verify(notebookQaProcessor, never()).process(any(), any(), any());
    }

    // ─────────────────────────── getHistories ────────────────────────────────

    @Test
    void returnsNotebookQaHistories() {
        Notebook notebook = createNotebook(1L, 10L);
        LocalDateTime firstCreatedAt = LocalDateTime.of(2026, 5, 13, 13, 20);
        LocalDateTime secondCreatedAt = firstCreatedAt.plusMinutes(3);
        NotebookQaHistory first = createHistory(1L, notebook, "RAG가 뭐야?", "검색 증강 생성입니다.", true, List.of(100L), firstCreatedAt);
        NotebookQaHistory second = createHistory(2L, notebook, "없는 내용?", "질문에 답변할 수 있는 문서 근거를 찾지 못했습니다.", false, null, secondCreatedAt);

        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));
        when(notebookQaHistoryRepository.findAllByNotebook_NotebookIdAndUser_UserIdAndDeletedAtIsNullOrderByCreatedAtAsc(10L, 1L))
                .thenReturn(List.of(first, second));

        List<NotebookQaHistoryResult> histories = notebookQaService.getHistories(1L, 10L);

        assertThat(histories).hasSize(2);
        assertThat(histories.get(0).qaHistoryId()).isEqualTo(1L);
        assertThat(histories.get(0).question()).isEqualTo("RAG가 뭐야?");
        assertThat(histories.get(0).answer()).isEqualTo("검색 증강 생성입니다.");
        assertThat(histories.get(0).answerable()).isTrue();
        assertThat(histories.get(0).citedSectionIds()).containsExactly(100L);
        assertThat(histories.get(0).createdAt()).isEqualTo(firstCreatedAt);

        assertThat(histories.get(1).qaHistoryId()).isEqualTo(2L);
        assertThat(histories.get(1).answerable()).isFalse();
        assertThat(histories.get(1).citedSectionIds()).isEmpty();
        assertThat(histories.get(1).createdAt()).isEqualTo(secondCreatedAt);
    }

    // ───────────────────────────── helpers ───────────────────────────────────

    private Notebook createNotebook(Long userId, Long notebookId) {
        UserAccount user = UserAccount.create("user" + userId + "@example.com", "hash");
        ReflectionTestUtils.setField(user, "userId", userId);
        Notebook notebook = Notebook.create(user, "강의 노트");
        ReflectionTestUtils.setField(notebook, "notebookId", notebookId);
        return notebook;
    }

    private NotebookQaHistory createHistory(
            Long qaHistoryId,
            Notebook notebook,
            String question,
            String answer,
            boolean answerable,
            List<Long> citedSectionIds,
            LocalDateTime createdAt
    ) {
        NotebookQaHistory history = NotebookQaHistory.create(
                notebook.getUser(),
                notebook,
                question,
                answer,
                answerable,
                citedSectionIds
        );
        ReflectionTestUtils.setField(history, "qaHistoryId", qaHistoryId);
        ReflectionTestUtils.setField(history, "createdAt", createdAt);
        return history;
    }
}
