package org.example.snow.ai.application;

import lombok.extern.slf4j.Slf4j;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class QaJobStore {

    private final ConcurrentHashMap<String, QaJob> store = new ConcurrentHashMap<>();

    /**
     * 새 QaJob 생성 후 QUEUED 상태로 저장.
     * userId, notebookId는 polling 시 소유권 검증에 사용된다.
     */
    public QaJob create(Long userId, Long notebookId) {
        String qaJobId = UUID.randomUUID().toString();
        QaJob job = new QaJob(qaJobId, userId, notebookId);
        store.put(qaJobId, job);
        log.debug("QaJob 생성 | qaJobId={} userId={} notebookId={}", qaJobId, userId, notebookId);
        return job;
    }

    public Optional<QaJob> get(String qaJobId) {
        return Optional.ofNullable(store.get(qaJobId));
    }

    /**
     * 소유권 검증.
     * 존재하지 않으면 QA_JOB_NOT_FOUND, 소유자가 다르면 QA_JOB_ACCESS_DENIED.
     */
    public QaJob getWithOwnershipCheck(String qaJobId, Long userId, Long notebookId) {
        QaJob job = store.get(qaJobId);
        if (job == null) {
            throw new BusinessException(ErrorCode.QA_JOB_NOT_FOUND);
        }
        if (!job.getUserId().equals(userId) || !job.getNotebookId().equals(notebookId)) {
            throw new BusinessException(ErrorCode.QA_JOB_ACCESS_DENIED);
        }
        return job;
    }

    public void markRunning(String qaJobId) {
        QaJob job = store.get(qaJobId);
        if (job != null) {
            job.markRunning();
            log.debug("QaJob RUNNING | qaJobId={}", qaJobId);
        }
    }

    public void markCompleted(String qaJobId, NotebookQaResult result) {
        QaJob job = store.get(qaJobId);
        if (job != null) {
            job.markCompleted(result);
            log.debug("QaJob COMPLETED | qaJobId={}", qaJobId);
        }
    }

    public void markFailed(String qaJobId) {
        QaJob job = store.get(qaJobId);
        if (job != null) {
            job.markFailed();
            log.warn("QaJob FAILED | qaJobId={}", qaJobId);
        }
    }
}
