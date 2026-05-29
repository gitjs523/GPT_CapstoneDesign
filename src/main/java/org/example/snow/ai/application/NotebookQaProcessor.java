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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 노트북 Q&A 실제 처리 빈.
 *
 * NotebookQaService.ask()가 큐에 제출하는 task는 이 빈의 process()를 호출한다.
 * 별도 빈으로 분리한 이유: 큐 task(람다) 내부에서 NotebookQaService 자신의
 * @Transactional 메서드를 this.xxx()로 호출하면 Spring 프록시를 우회하여
 * 트랜잭션이 적용되지 않는다. 빈으로 주입받아 호출해야 프록시가 정상 동작한다.
 */
@Service
@RequiredArgsConstructor
public class NotebookQaProcessor {

    private static final int RETRIEVAL_LIMIT = 5;
    private static final String NO_CONTEXT_ANSWER = "질문에 답변할 수 있는 문서 근거를 찾지 못했습니다.";

    private final NotebookRepository notebookRepository;
    private final EmbeddingSearchService embeddingSearchService;
    private final OllamaService ollamaService;
    private final NotebookQaHistoryRepository notebookQaHistoryRepository;

    @Transactional
    public NotebookQaResult process(Long userId, Long notebookId, String question) {
        Notebook notebook = notebookRepository.findByNotebookIdAndDeletedAtIsNull(notebookId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTEBOOK_NOT_FOUND));

        List<RetrievedSection> retrievedSections = embeddingSearchService.searchSimilarSections(
                notebookId, question, RETRIEVAL_LIMIT
        );

        GeneratedAnswer generatedAnswer = retrievedSections.isEmpty()
                ? new GeneratedAnswer(NO_CONTEXT_ANSWER, List.of(), false)
                : ollamaService.generateGroundedAnswer(new AnswerGenerationCommand(question, retrievedSections));

        List<Long> citedSectionIds = generatedAnswer.answerable()
                ? extractValidCitedSectionIds(generatedAnswer, retrievedSections)
                : List.of();

        NotebookQaHistory history = notebookQaHistoryRepository.save(NotebookQaHistory.create(
                notebook.getUser(),
                notebook,
                question,
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

    private List<Long> extractValidCitedSectionIds(GeneratedAnswer generatedAnswer,
                                                    List<RetrievedSection> retrievedSections) {
        Set<Long> retrievedSectionIds = retrievedSections.stream()
                .map(RetrievedSection::sectionId)
                .map(this::parseSectionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> citedSectionIds = generatedAnswer.citedSectionIds().stream()
                .map(this::parseSectionId)
                .filter(Objects::nonNull)
                .filter(retrievedSectionIds::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return List.copyOf(citedSectionIds);
    }

    private Long parseSectionId(String sectionId) {
        if (sectionId == null || sectionId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(sectionId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
