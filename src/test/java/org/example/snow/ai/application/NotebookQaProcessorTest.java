package org.example.snow.ai.application;

import org.example.snow.ai.domain.NotebookQaHistory;
import org.example.snow.ai.infra.NotebookQaHistoryRepository;
import org.example.snow.notebook.domain.Notebook;
import org.example.snow.notebook.infra.NotebookRepository;
import org.example.snow.user.domain.UserAccount;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotebookQaProcessorTest {

    private final NotebookRepository notebookRepository = mock(NotebookRepository.class);
    private final EmbeddingSearchService embeddingSearchService = mock(EmbeddingSearchService.class);
    private final OllamaService ollamaService = mock(OllamaService.class);
    private final NotebookQaHistoryRepository notebookQaHistoryRepository = mock(NotebookQaHistoryRepository.class);

    private final NotebookQaProcessor processor = new NotebookQaProcessor(
            notebookRepository,
            embeddingSearchService,
            ollamaService,
            notebookQaHistoryRepository
    );

    @Test
    void process_retrievedSections이_있으면_LLM_호출_후_history_저장() {
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

        NotebookQaResult result = processor.process(1L, 10L, "RAG가 뭐야?");

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
    void process_retrievedSections이_없으면_LLM_호출_생략하고_unanswerable_history_저장() {
        Notebook notebook = createNotebook(1L, 10L);

        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));
        when(embeddingSearchService.searchSimilarSections(10L, "없는 내용?", 5)).thenReturn(List.of());
        when(notebookQaHistoryRepository.save(any())).thenAnswer(invocation -> {
            NotebookQaHistory history = invocation.getArgument(0);
            ReflectionTestUtils.setField(history, "qaHistoryId", 78L);
            return history;
        });

        NotebookQaResult result = processor.process(1L, 10L, "없는 내용?");

        ArgumentCaptor<NotebookQaHistory> historyCaptor = ArgumentCaptor.forClass(NotebookQaHistory.class);
        verify(ollamaService, never()).generateGroundedAnswer(any());
        verify(notebookQaHistoryRepository).save(historyCaptor.capture());

        assertThat(historyCaptor.getValue().isAnswerable()).isFalse();
        assertThat(result.qaHistoryId()).isEqualTo(78L);
        assertThat(result.answerable()).isFalse();
        assertThat(result.citedSectionIds()).isEmpty();
    }

    // ───────────────────────────── helpers ───────────────────────────────────

    private Notebook createNotebook(Long userId, Long notebookId) {
        UserAccount user = UserAccount.create("user" + userId + "@example.com", "hash");
        ReflectionTestUtils.setField(user, "userId", userId);
        Notebook notebook = Notebook.create(user, "강의 노트");
        ReflectionTestUtils.setField(notebook, "notebookId", notebookId);
        return notebook;
    }
}
