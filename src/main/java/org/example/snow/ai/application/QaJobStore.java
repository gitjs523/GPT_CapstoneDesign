package org.example.snow.ai.application;

import lombok.extern.slf4j.Slf4j;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class QaJobStore {

    private final ConcurrentHashMap<String, QaJob> store = new ConcurrentHashMap<>();

    /**
     * 종료(COMPLETED/FAILED)된 QaJob을 메모리에서 제거하기까지 유지하는 시간(분).
     * QaJobStore는 인메모리 맵이라 제거 로직이 없으면 종료된 job이 무한 누적된다.
     * 클라이언트가 polling으로 결과를 회수할 시간을 확보한 뒤 정리한다.
     */
    @Value("${qa-job.retention-minutes}")
    private long retentionMinutes;

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

    /**
     * 종료된 지 retentionMinutes를 초과한 QaJob을 주기적으로 제거한다.
     * QUEUED/RUNNING(처리 중) job은 제거 대상이 아니다 — 종료 시각이 찍힌 job만 정리한다.
     * 서버 비정상 종료 시 인메모리 job은 전부 소실되며, 이는 재시작 후 어차피
     * FAILED로 정리됐어야 할 작업이 한발 먼저 사라진 것이므로 별도 복구하지 않는다.
     */
    @Scheduled(fixedDelayString = "${qa-job.cleanup-interval-ms}")
    public void evictExpiredJobs() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(retentionMinutes);
        int before = store.size();
        store.values().removeIf(job ->
                job.isTerminal()
                        && job.getFinishedAt() != null
                        && job.getFinishedAt().isBefore(threshold));
        int removed = before - store.size();
        if (removed > 0) {
            log.info("QaJob eviction 완료 | 제거={} 잔여={}", removed, store.size());
        }
    }
}
