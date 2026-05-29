package org.example.snow.ai.application;

public class QaJob {

    private final String qaJobId;
    private final Long userId;
    private final Long notebookId;
    private volatile QaJobStatus status;
    private volatile NotebookQaResult result;

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

    void markRunning() {
        this.status = QaJobStatus.RUNNING;
    }

    void markCompleted(NotebookQaResult result) {
        this.result = result;
        this.status = QaJobStatus.COMPLETED;
    }

    void markFailed() {
        this.status = QaJobStatus.FAILED;
    }
}
