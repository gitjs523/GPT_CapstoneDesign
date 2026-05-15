package org.example.snow.ai.application;

import lombok.RequiredArgsConstructor;
import org.example.snow.ai.domain.GeneratedQuiz;
import org.example.snow.ai.domain.QuizQaHistory;
import org.example.snow.ai.infra.GeneratedQuizRepository;
import org.example.snow.ai.infra.QuizQaHistoryRepository;
import org.example.snow.document.domain.Section;
import org.example.snow.document.infra.SectionRepository;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class QuizQaService {

    private final GeneratedQuizRepository generatedQuizRepository;
    private final SectionRepository sectionRepository;
    private final OllamaService ollamaService;
    private final QuizQaHistoryRepository quizQaHistoryRepository;

    @Transactional(readOnly = true)
    public List<QuizQaHistoryResult> getHistories(Long userId, Long quizId) {
        getQuizWithOwnershipCheck(userId, quizId);
        return quizQaHistoryRepository
                .findAllByQuiz_QuizIdAndUser_UserIdAndDeletedAtIsNullOrderByCreatedAtAsc(quizId, userId)
                .stream()
                .map(QuizQaHistoryResult::from)
                .toList();
    }

    @Transactional
    public QuizQaResult ask(Long userId, Long quizId, String question) {
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("질문은 필수입니다.");
        }

        GeneratedQuiz quiz = getQuizWithOwnershipCheck(userId, quizId);
        String trimmedQuestion = question.trim();
        List<RetrievedSection> sourceSections = loadSourceSections(quiz);

        GeneratedAnswer generatedAnswer = ollamaService.generateQuizAnswer(new QuizAnswerGenerationCommand(
                trimmedQuestion,
                quiz,
                sourceSections
        ));

        QuizQaHistory history = quizQaHistoryRepository.save(QuizQaHistory.create(
                quiz.getGenerationJob().getUser(),
                quiz,
                trimmedQuestion,
                generatedAnswer.answer(),
                generatedAnswer.answerable()
        ));

        return new QuizQaResult(
                history.getQaHistoryId(),
                generatedAnswer.answer(),
                generatedAnswer.answerable()
        );
    }

    private GeneratedQuiz getQuizWithOwnershipCheck(Long userId, Long quizId) {
        GeneratedQuiz quiz = generatedQuizRepository.findByQuizIdAndDeletedAtIsNull(quizId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND));
        if (!quiz.getGenerationJob().getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.QUIZ_ACCESS_DENIED);
        }
        return quiz;
    }

    private List<RetrievedSection> loadSourceSections(GeneratedQuiz quiz) {
        List<Long> sourceSectionIds = quiz.getSourceSectionIds();
        if (sourceSectionIds == null || sourceSectionIds.isEmpty()) {
            return List.of();
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
                .map(section -> new RetrievedSection(
                        String.valueOf(section.getSectionId()),
                        section.getHeading(),
                        section.getContent(),
                        section.getDocument().getOriginalFileName(),
                        section.getSourceStartIndex(),
                        section.getSourceEndIndex(),
                        orderBySectionId.get(section.getSectionId()) + 1,
                        null
                ))
                .toList();
    }
}
