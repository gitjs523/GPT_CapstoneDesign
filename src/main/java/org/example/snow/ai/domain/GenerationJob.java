package org.example.snow.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.snow.notebook.domain.Notebook;
import org.example.snow.user.domain.UserAccount;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "generation_job")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GenerationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_id")
    private Long jobId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_generation_job_user"))
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notebook_id", nullable = false, foreignKey = @ForeignKey(name = "fk_generation_job_notebook"))
    private Notebook notebook;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prompt_template_id", foreignKey = @ForeignKey(name = "fk_generation_job_prompt_template"))
    private PromptTemplate promptTemplate;

    @Column(name = "scope_text", columnDefinition = "text")
    private String scopeText;

    @Column(name = "quiz_type", nullable = false, length = 20)
    private String quizType;

    @Column(name = "difficulty", nullable = false, length = 10)
    private String difficulty;

    @Column(name = "quiz_count")
    private Integer quizCount;

    @Column(name = "result_count")
    private Integer resultCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private GenerationJobStatus status;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private GenerationJob(
            UserAccount user,
            Notebook notebook,
            PromptTemplate promptTemplate,
            String scopeText,
            String quizType,
            String difficulty,
            Integer quizCount,
            String modelName
    ) {
        this.user = user;
        this.notebook = notebook;
        this.promptTemplate = promptTemplate;
        this.scopeText = scopeText;
        this.quizType = quizType;
        this.difficulty = difficulty;
        this.quizCount = quizCount;
        this.resultCount = 0;
        this.status = GenerationJobStatus.QUEUED;
        this.modelName = modelName;
    }

    public static GenerationJob create(
            UserAccount user,
            Notebook notebook,
            PromptTemplate promptTemplate,
            String scopeText,
            String quizType,
            String difficulty,
            Integer quizCount,
            String modelName
    ) {
        return new GenerationJob(user, notebook, promptTemplate, scopeText, quizType, difficulty, quizCount, modelName);
    }

    public void start() {
        this.status = GenerationJobStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
    }

    public void complete(int resultCount) {
        this.status = GenerationJobStatus.COMPLETED;
        this.resultCount = resultCount;
        this.finishedAt = LocalDateTime.now();
    }

    public void partialComplete(int resultCount) {
        this.status = GenerationJobStatus.PARTIAL_COMPLETED;
        this.resultCount = resultCount;
        this.finishedAt = LocalDateTime.now();
    }

    public void fail(int resultCount) {
        this.status = GenerationJobStatus.FAILED;
        this.resultCount = resultCount;
        this.finishedAt = LocalDateTime.now();
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
