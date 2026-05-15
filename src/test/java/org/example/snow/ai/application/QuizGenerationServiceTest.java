package org.example.snow.ai.application;

import org.example.snow.ai.domain.GeneratedQuiz;
import org.example.snow.ai.domain.GenerationContext;
import org.example.snow.ai.domain.GenerationJob;
import org.example.snow.ai.domain.GenerationJobStatus;
import org.example.snow.ai.infra.GeneratedQuizRepository;
import org.example.snow.ai.infra.GenerationContextRepository;
import org.example.snow.ai.infra.GenerationJobRepository;
import org.example.snow.ai.infra.PromptTemplateRepository;
import org.example.snow.document.domain.Document;
import org.example.snow.document.domain.ExtractedSection;
import org.example.snow.document.domain.Section;
import org.example.snow.document.domain.SourceUnitType;
import org.example.snow.document.infra.SectionRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuizGenerationServiceTest {

    private final NotebookRepository notebookRepository = mock(NotebookRepository.class);
    private final SectionRepository sectionRepository = mock(SectionRepository.class);
    private final PromptTemplateRepository promptTemplateRepository = mock(PromptTemplateRepository.class);
    private final GenerationJobRepository generationJobRepository = mock(GenerationJobRepository.class);
    private final GenerationContextRepository generationContextRepository = mock(GenerationContextRepository.class);
    private final GeneratedQuizRepository generatedQuizRepository = mock(GeneratedQuizRepository.class);
    private final EmbeddingSearchService embeddingSearchService = mock(EmbeddingSearchService.class);
    private final OllamaService ollamaService = mock(OllamaService.class);

    private final QuizGenerationService quizGenerationService = new QuizGenerationService(
            notebookRepository,
            sectionRepository,
            promptTemplateRepository,
            generationJobRepository,
            generationContextRepository,
            generatedQuizRepository,
            embeddingSearchService,
            ollamaService
    );

    @Test
    void createsGenerationJobContextsAndQuizzes() {
        ReflectionTestUtils.setField(quizGenerationService, "chatModelName", "qwen3:4b-q4_K_M");
        Notebook notebook = createNotebook(1L, 10L);
        Section section = createSection(notebook, 100L);
        RetrievedSection retrievedSection = new RetrievedSection(
                "100",
                "RAG",
                "RAG는 검색 증강 생성이다.",
                "lecture.pdf",
                1,
                1,
                1,
                0.93
        );

        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));
        when(promptTemplateRepository.findFirstByIsActiveTrueOrderByCreatedAtDesc()).thenReturn(Optional.empty());
        when(generationJobRepository.save(any())).thenAnswer(invocation -> {
            GenerationJob job = invocation.getArgument(0);
            ReflectionTestUtils.setField(job, "jobId", 55L);
            ReflectionTestUtils.setField(job, "createdAt", LocalDateTime.of(2026, 5, 13, 14, 0));
            return job;
        });
        when(embeddingSearchService.searchSimilarSections(10L, "RAG 단원", 8)).thenReturn(List.of(retrievedSection));
        when(sectionRepository.findAllById(List.of(100L))).thenReturn(List.of(section));
        when(ollamaService.generateQuiz(any()))
                .thenReturn(new GeneratedQuizDraft(
                        "MULTIPLE_CHOICE",
                        "RAG의 의미로 가장 알맞은 것은?",
                        "[\"검색 증강 생성\",\"정렬 알고리즘\"]",
                        "검색 증강 생성",
                        "RAG는 검색된 문맥을 활용해 답변이나 문제를 생성하는 방식이다.",
                        List.of(100L)
                ));
        when(generatedQuizRepository.save(any())).thenAnswer(invocation -> {
            GeneratedQuiz quiz = invocation.getArgument(0);
            ReflectionTestUtils.setField(quiz, "quizId", 900L + quiz.getQuizOrder());
            ReflectionTestUtils.setField(quiz, "createdAt", LocalDateTime.of(2026, 5, 13, 14, quiz.getQuizOrder()));
            return quiz;
        });

        QuizGenerationJobResult result = quizGenerationService.generate(
                1L,
                10L,
                new QuizGenerationCommand("RAG 단원", "MULTIPLE_CHOICE", "중", 2)
        );

        ArgumentCaptor<Iterable<GenerationContext>> contextCaptor = ArgumentCaptor.forClass(Iterable.class);
        ArgumentCaptor<QuizGenerationPrompt> promptCaptor = ArgumentCaptor.forClass(QuizGenerationPrompt.class);
        verify(generationContextRepository).saveAll(contextCaptor.capture());
        verify(ollamaService, org.mockito.Mockito.times(2)).generateQuiz(promptCaptor.capture());

        assertThat(contextCaptor.getValue()).hasSize(1);
        assertThat(promptCaptor.getAllValues())
                .extracting(QuizGenerationPrompt::quizOrder)
                .containsExactly(1, 2);
        assertThat(result.jobId()).isEqualTo(55L);
        assertThat(result.status()).isEqualTo(GenerationJobStatus.COMPLETED);
        assertThat(result.resultCount()).isEqualTo(2);
        assertThat(result.quizzes()).hasSize(2);
        assertThat(result.quizzes().get(0).sourceSectionIds()).containsExactly(100L);
    }

    private Notebook createNotebook(Long userId, Long notebookId) {
        UserAccount user = UserAccount.create("user" + userId + "@example.com", "hash");
        ReflectionTestUtils.setField(user, "userId", userId);
        Notebook notebook = Notebook.create(user, "강의 노트");
        ReflectionTestUtils.setField(notebook, "notebookId", notebookId);
        return notebook;
    }

    private Section createSection(Notebook notebook, Long sectionId) {
        Document document = Document.create(notebook, "lecture.pdf", "lecture.pdf", "PDF", 100L);
        ReflectionTestUtils.setField(document, "documentId", 200L);
        Section section = Section.create(document, new ExtractedSection(
                1,
                "RAG",
                "RAG는 검색 증강 생성이다.",
                SourceUnitType.PAGE,
                1,
                1,
                List.of(1)
        ));
        ReflectionTestUtils.setField(section, "sectionId", sectionId);
        return section;
    }
}
