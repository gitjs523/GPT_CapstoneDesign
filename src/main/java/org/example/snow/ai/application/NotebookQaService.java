package org.example.snow.ai.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.snow.ai.infra.NotebookQaHistoryRepository;
import org.example.snow.document.application.DocumentService;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.example.snow.global.queue.ModelQueueService;
import org.example.snow.notebook.domain.Notebook;
import org.example.snow.notebook.infra.NotebookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotebookQaService {

    private final NotebookRepository notebookRepository;
    private final NotebookQaHistoryRepository notebookQaHistoryRepository;
    private final DocumentService documentService;
    private final ModelQueueService modelQueueService;
    private final QaJobStore qaJobStore;
    private final NotebookQaProcessor notebookQaProcessor;

    @Transactional(readOnly = true)
    public List<NotebookQaHistoryResult> getHistories(Long userId, Long notebookId) {
        getNotebookWithOwnershipCheck(userId, notebookId);
        return notebookQaHistoryRepository
                .findAllByNotebook_NotebookIdAndUser_UserIdAndDeletedAtIsNullOrderByCreatedAtAsc(notebookId, userId)
                .stream()
                .map(NotebookQaHistoryResult::from)
                .toList();
    }

    /**
     * 질문을 생성 큐에 등록하고 QaJob을 반환한다.
     * 실제 처리(임베딩 검색, LLM 호출, 이력 저장)는 큐 executor 스레드에서 비동기로 수행된다.
     */
    @Transactional(readOnly = true)
    public QaJob ask(Long userId, Long notebookId, String question) {
        if (!StringUtils.hasText(question)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        getNotebookWithOwnershipCheck(userId, notebookId);
        documentService.validateNoneAnalyzing(notebookId);
        documentService.validateHasAnalyzedDocument(notebookId);

        if (!modelQueueService.canAcceptGeneration()) {
            throw new BusinessException(ErrorCode.MODEL_QUEUE_FULL);
        }

        QaJob qaJob = qaJobStore.create(userId, notebookId);
        String qaJobId = qaJob.getQaJobId();
        String trimmedQuestion = question.trim();

        boolean submitted = modelQueueService.submitGeneration(() -> {
            try {
                qaJobStore.markRunning(qaJobId);
                NotebookQaResult result = notebookQaProcessor.process(userId, notebookId, trimmedQuestion);
                qaJobStore.markCompleted(qaJobId, result);
            } catch (Exception e) {
                log.error("Q&A 처리 실패 qaJobId={} notebookId={}", qaJobId, notebookId, e);
                qaJobStore.markFailed(qaJobId);
            }
        });

        if (!submitted) {
            // canAcceptGeneration() 통과 후 극히 드문 race condition 대비
            qaJobStore.markFailed(qaJobId);
            throw new BusinessException(ErrorCode.MODEL_QUEUE_FULL);
        }

        return qaJob;
    }

    /**
     * 클라이언트 polling용 — QaJob 상태 및 결과 조회.
     */
    public QaJob getQaJob(Long userId, Long notebookId, String qaJobId) {
        return qaJobStore.getWithOwnershipCheck(qaJobId, userId, notebookId);
    }

    @Transactional(readOnly = true)
    private Notebook getNotebookWithOwnershipCheck(Long userId, Long notebookId) {
        Notebook notebook = notebookRepository.findByNotebookIdAndDeletedAtIsNull(notebookId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTEBOOK_NOT_FOUND));
        if (!notebook.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOTEBOOK_ACCESS_DENIED);
        }
        return notebook;
    }
}
