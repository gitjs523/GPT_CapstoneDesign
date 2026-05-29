package org.example.snow.ai.application;

import java.time.LocalDateTime;

public class QaJob {

    private final String qaJobId;
    private final Long userId;
    private final Long notebookId;
    private volatile QaJobStatus status;
    private volatile NotebookQaResult result;
    // 종료(COMPLETED/FAILED) 전환 시각. eviction 만료 판정 기준이며, 종료 전에는 null이다.
    private volatile LocalDateTime finishedAt;

    public QaJob(String qaJobId, Long userId, Long notebookId) {
        this.qaJobId = qaJobId;
        this.userId = userId;
        this.notebookId = notebookId;
        this.status = QaJobStatus.QUEUED;
    }

    public String getQaJobId() {
        return qaJobId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getNotebookId() {
        return notebookId;
    }

    public QaJobStatus getStatus() {
        return status;
    }

    public NotebookQaResult getResult() {
        return result;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public boolean isTerminal() {
        return status == QaJobStatus.COMPLETED || status == QaJobStatus.FAILED;
    }

    void markRunning() {
        this.status = QaJobStatus.RUNNING;
    }

    void markCompleted(NotebookQaResult result) {
        this.result = result;
        this.status = QaJobStatus.COMPLETED;
        this.finishedAt = LocalDateTime.now();
    }

    void markFailed() {
        this.status = QaJobStatus.FAILED;
        this.finishedAt = LocalDateTime.now();
    }
}
