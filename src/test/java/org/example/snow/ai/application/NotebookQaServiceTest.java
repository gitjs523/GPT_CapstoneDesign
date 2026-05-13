package org.example.snow.ai.application;

import org.example.snow.ai.domain.NotebookQaHistory;
import org.example.snow.ai.infra.NotebookQaHistoryRepository;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.notebook.domain.Notebook;
import org.example.snow.notebook.infra.NotebookRepository;
import org.example.snow.user.domain.UserAccount;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

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

class NotebookQaServiceTest {

    private final NotebookRepository notebookRepository = mock(NotebookRepository.class);
    private final EmbeddingSearchService embeddingSearchService = mock(EmbeddingSearchService.class);
    private final OllamaService ollamaService = mock(OllamaService.class);
    private final NotebookQaHistoryRepository notebookQaHistoryRepository = mock(NotebookQaHistoryRepository.class);

    private final NotebookQaService notebookQaService = new NotebookQaService(
            notebookRepository,
            embeddingSearchService,
            ollamaService,
            notebookQaHistoryRepository
    );

    @Test
    void asksWithRetrievedSectionsAndSavesHistory() {
        Notebook notebook = createNotebook(1L, 10L);
        RetrievedSection section = new RetrievedSection(
                "100",
                "RAG",
                "RAG는 검색된 문맥을 기반으로 답변한다.",
                "lecture.pdf",
                1,
                2,
                1,
                0.91
        );

        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));
        when(embeddingSearchService.searchSimilarSections(10L, "RAG가 뭐야?", 5)).thenReturn(List.of(section));
        when(ollamaService.generateGroundedAnswer(any()))
                .thenReturn(new GeneratedAnswer("RAG는 검색 문맥을 근거로 답변하는 방식입니다.", List.of("100"), true));
        when(notebookQaHistoryRepository.save(any())).thenAnswer(invocation -> {
            NotebookQaHistory history = invocation.getArgument(0);
            ReflectionTestUtils.setField(history, "qaHistoryId", 77L);
            return history;
        });

        NotebookQaResult result = notebookQaService.ask(1L, 10L, " RAG가 뭐야? ");

        ArgumentCaptor<AnswerGenerationCommand> commandCaptor = ArgumentCaptor.forClass(AnswerGenerationCommand.class);
        ArgumentCaptor<NotebookQaHistory> historyCaptor = ArgumentCaptor.forClass(NotebookQaHistory.class);
        verify(ollamaService).generateGroundedAnswer(commandCaptor.capture());
        verify(notebookQaHistoryRepository).save(historyCaptor.capture());

        assertThat(commandCaptor.getValue().question()).isEqualTo("RAG가 뭐야?");
        assertThat(commandCaptor.getValue().sections()).containsExactly(section);

        NotebookQaHistory savedHistory = historyCaptor.getValue();
        assertThat(savedHistory.getUserQuestion()).isEqualTo("RAG가 뭐야?");
        assertThat(savedHistory.getAiAnswer()).isEqualTo("RAG는 검색 문맥을 근거로 답변하는 방식입니다.");
        assertThat(savedHistory.isAnswerable()).isTrue();
        assertThat(savedHistory.getCitedSectionIds()).containsExactly(100L);

        assertThat(result.qaHistoryId()).isEqualTo(77L);
        assertThat(result.answer()).isEqualTo("RAG는 검색 문맥을 근거로 답변하는 방식입니다.");
        assertThat(result.answerable()).isTrue();
        assertThat(result.citedSectionIds()).containsExactly(100L);
    }

    @Test
    void savesUnanswerableHistoryWhenNoContextIsFound() {
        Notebook notebook = createNotebook(1L, 10L);

        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));
        when(embeddingSearchService.searchSimilarSections(10L, "없는 내용?", 5)).thenReturn(List.of());
        when(notebookQaHistoryRepository.save(any())).thenAnswer(invocation -> {
            NotebookQaHistory history = invocation.getArgument(0);
            ReflectionTestUtils.setField(history, "qaHistoryId", 78L);
            return history;
        });

        NotebookQaResult result = notebookQaService.ask(1L, 10L, "없는 내용?");

        ArgumentCaptor<NotebookQaHistory> historyCaptor = ArgumentCaptor.forClass(NotebookQaHistory.class);
        verify(ollamaService, never()).generateGroundedAnswer(any());
        verify(notebookQaHistoryRepository).save(historyCaptor.capture());

        assertThat(historyCaptor.getValue().isAnswerable()).isFalse();
        assertThat(historyCaptor.getValue().getCitedSectionIds()).isNull();
        assertThat(result.qaHistoryId()).isEqualTo(78L);
        assertThat(result.answerable()).isFalse();
        assertThat(result.citedSectionIds()).isEmpty();
    }

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

    @Test
    void rejectsNotebookOwnedByAnotherUser() {
        Notebook notebook = createNotebook(2L, 10L);

        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));

        assertThatThrownBy(() -> notebookQaService.ask(1L, 10L, "질문"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("해당 노트북에 접근할 권한이 없습니다.");

        verify(embeddingSearchService, never()).searchSimilarSections(any(), any(), any(Integer.class));
        verify(notebookQaHistoryRepository, never()).save(any());
    }

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
