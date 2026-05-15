package org.example.snow.ai.application;

import org.example.snow.ai.domain.GeneratedQuiz;
import org.example.snow.ai.domain.GenerationJob;
import org.example.snow.ai.domain.QuizQaHistory;
import org.example.snow.ai.infra.GeneratedQuizRepository;
import org.example.snow.ai.infra.QuizQaHistoryRepository;
import org.example.snow.document.domain.Document;
import org.example.snow.document.domain.ExtractedSection;
import org.example.snow.document.domain.Section;
import org.example.snow.document.domain.SourceUnitType;
import org.example.snow.document.infra.SectionRepository;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.notebook.domain.Notebook;
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

class QuizQaServiceTest {

    private final GeneratedQuizRepository generatedQuizRepository = mock(GeneratedQuizRepository.class);
    private final SectionRepository sectionRepository = mock(SectionRepository.class);
    private final OllamaService ollamaService = mock(OllamaService.class);
    private final QuizQaHistoryRepository quizQaHistoryRepository = mock(QuizQaHistoryRepository.class);

    private final QuizQaService quizQaService = new QuizQaService(
            generatedQuizRepository,
            sectionRepository,
            ollamaService,
            quizQaHistoryRepository
    );

    @Test
    void asksWithQuizAndSourceSectionsAndSavesHistory() {
        Notebook notebook = createNotebook(1L, 10L);
        GeneratedQuiz quiz = createQuiz(notebook, 50L, List.of(100L));
        Section section = createSection(notebook, 100L);

        when(generatedQuizRepository.findByQuizIdAndDeletedAtIsNull(50L)).thenReturn(Optional.of(quiz));
        when(sectionRepository.findAllBySectionIdInAndDeletedAtIsNull(List.of(100L))).thenReturn(List.of(section));
        when(ollamaService.generateQuizAnswer(any()))
                .thenReturn(new GeneratedAnswer("정답은 검색 증강 생성이기 때문입니다.", List.of("100"), true));
        when(quizQaHistoryRepository.save(any())).thenAnswer(invocation -> {
            QuizQaHistory history = invocation.getArgument(0);
            ReflectionTestUtils.setField(history, "qaHistoryId", 90L);
            return history;
        });

        QuizQaResult result = quizQaService.ask(1L, 50L, " 왜 이게 정답이야? ");

        ArgumentCaptor<QuizAnswerGenerationCommand> commandCaptor = ArgumentCaptor.forClass(QuizAnswerGenerationCommand.class);
        ArgumentCaptor<QuizQaHistory> historyCaptor = ArgumentCaptor.forClass(QuizQaHistory.class);
        verify(ollamaService).generateQuizAnswer(commandCaptor.capture());
        verify(quizQaHistoryRepository).save(historyCaptor.capture());

        QuizAnswerGenerationCommand command = commandCaptor.getValue();
        assertThat(command.question()).isEqualTo("왜 이게 정답이야?");
        assertThat(command.quiz()).isSameAs(quiz);
        assertThat(command.sourceSections()).hasSize(1);
        assertThat(command.sourceSections().get(0).sectionId()).isEqualTo("100");
        assertThat(command.sourceSections().get(0).text()).isEqualTo("RAG는 검색된 문맥을 기반으로 답변을 생성한다.");

        QuizQaHistory savedHistory = historyCaptor.getValue();
        assertThat(savedHistory.getQuiz()).isSameAs(quiz);
        assertThat(savedHistory.getUserQuestion()).isEqualTo("왜 이게 정답이야?");
        assertThat(savedHistory.getAiAnswer()).isEqualTo("정답은 검색 증강 생성이기 때문입니다.");
        assertThat(savedHistory.isAnswerable()).isTrue();

        assertThat(result.qaHistoryId()).isEqualTo(90L);
        assertThat(result.answer()).isEqualTo("정답은 검색 증강 생성이기 때문입니다.");
        assertThat(result.answerable()).isTrue();
    }

    @Test
    void asksWithQuizOnlyWhenSourceSectionsAreEmpty() {
        Notebook notebook = createNotebook(1L, 10L);
        GeneratedQuiz quiz = createQuiz(notebook, 51L, null);

        when(generatedQuizRepository.findByQuizIdAndDeletedAtIsNull(51L)).thenReturn(Optional.of(quiz));
        when(ollamaService.generateQuizAnswer(any()))
                .thenReturn(new GeneratedAnswer("기존 해설에 따르면 문맥을 검색해서 답변하기 때문입니다.", List.of(), true));
        when(quizQaHistoryRepository.save(any())).thenAnswer(invocation -> {
            QuizQaHistory history = invocation.getArgument(0);
            ReflectionTestUtils.setField(history, "qaHistoryId", 91L);
            return history;
        });

        QuizQaResult result = quizQaService.ask(1L, 51L, "해설을 쉽게 말해줘");

        ArgumentCaptor<QuizAnswerGenerationCommand> commandCaptor = ArgumentCaptor.forClass(QuizAnswerGenerationCommand.class);
        verify(ollamaService).generateQuizAnswer(commandCaptor.capture());
        verify(sectionRepository, never()).findAllBySectionIdInAndDeletedAtIsNull(any());

        assertThat(commandCaptor.getValue().sourceSections()).isEmpty();
        assertThat(result.qaHistoryId()).isEqualTo(91L);
        assertThat(result.answerable()).isTrue();
    }

    @Test
    void returnsQuizQaHistories() {
        Notebook notebook = createNotebook(1L, 10L);
        GeneratedQuiz quiz = createQuiz(notebook, 50L, List.of(100L));
        LocalDateTime firstCreatedAt = LocalDateTime.of(2026, 5, 14, 18, 30);
        LocalDateTime secondCreatedAt = firstCreatedAt.plusMinutes(2);
        QuizQaHistory first = createHistory(1L, quiz, "왜 정답?", "근거 문맥 때문입니다.", true, firstCreatedAt);
        QuizQaHistory second = createHistory(2L, quiz, "다른 개념?", "제공된 문맥만으로는 알 수 없습니다.", false, secondCreatedAt);

        when(generatedQuizRepository.findByQuizIdAndDeletedAtIsNull(50L)).thenReturn(Optional.of(quiz));
        when(quizQaHistoryRepository.findAllByQuiz_QuizIdAndUser_UserIdAndDeletedAtIsNullOrderByCreatedAtAsc(50L, 1L))
                .thenReturn(List.of(first, second));

        List<QuizQaHistoryResult> histories = quizQaService.getHistories(1L, 50L);

        assertThat(histories).hasSize(2);
        assertThat(histories.get(0).qaHistoryId()).isEqualTo(1L);
        assertThat(histories.get(0).question()).isEqualTo("왜 정답?");
        assertThat(histories.get(0).answer()).isEqualTo("근거 문맥 때문입니다.");
        assertThat(histories.get(0).answerable()).isTrue();
        assertThat(histories.get(0).createdAt()).isEqualTo(firstCreatedAt);

        assertThat(histories.get(1).qaHistoryId()).isEqualTo(2L);
        assertThat(histories.get(1).answerable()).isFalse();
        assertThat(histories.get(1).createdAt()).isEqualTo(secondCreatedAt);
    }

    @Test
    void rejectsQuizOwnedByAnotherUser() {
        Notebook notebook = createNotebook(2L, 10L);
        GeneratedQuiz quiz = createQuiz(notebook, 50L, List.of(100L));

        when(generatedQuizRepository.findByQuizIdAndDeletedAtIsNull(50L)).thenReturn(Optional.of(quiz));

        assertThatThrownBy(() -> quizQaService.ask(1L, 50L, "질문"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("해당 퀴즈에 접근할 권한이 없습니다.");

        verify(ollamaService, never()).generateQuizAnswer(any());
        verify(quizQaHistoryRepository, never()).save(any());
    }

    private Notebook createNotebook(Long userId, Long notebookId) {
        UserAccount user = UserAccount.create("user" + userId + "@example.com", "hash");
        ReflectionTestUtils.setField(user, "userId", userId);
        Notebook notebook = Notebook.create(user, "강의 노트");
        ReflectionTestUtils.setField(notebook, "notebookId", notebookId);
        return notebook;
    }

    private GeneratedQuiz createQuiz(Notebook notebook, Long quizId, List<Long> sourceSectionIds) {
        GenerationJob job = GenerationJob.create(
                notebook.getUser(),
                notebook,
                null,
                "RAG",
                "객관식",
                "중",
                1,
                "qwen3:4b-q4_K_M"
        );
        ReflectionTestUtils.setField(job, "jobId", 20L);
        GeneratedQuiz quiz = GeneratedQuiz.create(
                job,
                1,
                "객관식",
                "RAG의 핵심 특징은 무엇인가?",
                "[\"검색된 문맥을 활용한다\", \"항상 추측한다\"]",
                "검색된 문맥을 활용한다",
                "RAG는 검색된 문맥을 기반으로 답변을 생성한다.",
                sourceSectionIds
        );
        ReflectionTestUtils.setField(quiz, "quizId", quizId);
        return quiz;
    }

    private Section createSection(Notebook notebook, Long sectionId) {
        Document document = Document.create(notebook, "lecture.pdf", "lecture.pdf", "PDF", 100L);
        Section section = Section.create(document, new ExtractedSection(
                1,
                "RAG",
                "RAG는 검색된 문맥을 기반으로 답변을 생성한다.",
                SourceUnitType.PAGE,
                1,
                1,
                List.of(1)
        ));
        ReflectionTestUtils.setField(section, "sectionId", sectionId);
        return section;
    }

    private QuizQaHistory createHistory(
            Long qaHistoryId,
            GeneratedQuiz quiz,
            String question,
            String answer,
            boolean answerable,
            LocalDateTime createdAt
    ) {
        QuizQaHistory history = QuizQaHistory.create(
                quiz.getGenerationJob().getUser(),
                quiz,
                question,
                answer,
                answerable
        );
        ReflectionTestUtils.setField(history, "qaHistoryId", qaHistoryId);
        ReflectionTestUtils.setField(history, "createdAt", createdAt);
        return history;
    }
}
