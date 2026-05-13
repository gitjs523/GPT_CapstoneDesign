package org.example.snow.ai.application;

import lombok.RequiredArgsConstructor;
import org.example.snow.ai.domain.NotebookQaHistory;
import org.example.snow.ai.infra.NotebookQaHistoryRepository;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.example.snow.notebook.domain.Notebook;
import org.example.snow.notebook.infra.NotebookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotebookQaService {

    private static final int RETRIEVAL_LIMIT = 5;
    private static final String NO_CONTEXT_ANSWER = "질문에 답변할 수 있는 문서 근거를 찾지 못했습니다.";

    private final NotebookRepository notebookRepository;
    private final EmbeddingSearchService embeddingSearchService;
    private final OllamaService ollamaService;
    private final NotebookQaHistoryRepository notebookQaHistoryRepository;

    @Transactional(readOnly = true)
    public List<NotebookQaHistoryResult> getHistories(Long userId, Long notebookId) {
        getNotebookWithOwnershipCheck(userId, notebookId);
        return notebookQaHistoryRepository
                .findAllByNotebook_NotebookIdAndUser_UserIdAndDeletedAtIsNullOrderByCreatedAtAsc(notebookId, userId)
                .stream()
                .map(NotebookQaHistoryResult::from)
                .toList();
    }

    @Transactional
    public NotebookQaResult ask(Long userId, Long notebookId, String question) {
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("질문은 필수입니다.");
        }

        Notebook notebook = getNotebookWithOwnershipCheck(userId, notebookId);
        String trimmedQuestion = question.trim();
        List<RetrievedSection> retrievedSections = embeddingSearchService.searchSimilarSections(
                notebookId,
                trimmedQuestion,
                RETRIEVAL_LIMIT
        );

        GeneratedAnswer generatedAnswer = retrievedSections.isEmpty()
                ? new GeneratedAnswer(NO_CONTEXT_ANSWER, List.of(), false)
                : ollamaService.generateGroundedAnswer(new AnswerGenerationCommand(trimmedQuestion, retrievedSections));

        List<Long> citedSectionIds = generatedAnswer.answerable()
                ? extractValidCitedSectionIds(generatedAnswer, retrievedSections)
                : List.of();

        NotebookQaHistory history = notebookQaHistoryRepository.save(NotebookQaHistory.create(
                notebook.getUser(),
                notebook,
                trimmedQuestion,
                generatedAnswer.answer(),
                generatedAnswer.answerable(),
                citedSectionIds
        ));

        return new NotebookQaResult(
                history.getQaHistoryId(),
                generatedAnswer.answer(),
                generatedAnswer.answerable(),
                citedSectionIds
        );
    }

    private Notebook getNotebookWithOwnershipCheck(Long userId, Long notebookId) {
        Notebook notebook = notebookRepository.findByNotebookIdAndDeletedAtIsNull(notebookId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTEBOOK_NOT_FOUND));
        if (!notebook.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOTEBOOK_ACCESS_DENIED);
        }
        return notebook;
    }

    private List<Long> extractValidCitedSectionIds(GeneratedAnswer generatedAnswer, List<RetrievedSection> retrievedSections) {
        Set<Long> retrievedSectionIds = retrievedSections.stream()
                .map(RetrievedSection::sectionId)
                .map(this::parseSectionId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        Set<Long> citedSectionIds = generatedAnswer.citedSectionIds().stream()
                .map(this::parseSectionId)
                .filter(Objects::nonNull)
                .filter(retrievedSectionIds::contains)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        return List.copyOf(citedSectionIds);
    }

    private Long parseSectionId(String sectionId) {
        if (!StringUtils.hasText(sectionId)) {
            return null;
        }
        try {
            return Long.valueOf(sectionId.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
