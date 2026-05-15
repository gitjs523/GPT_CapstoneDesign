package org.example.snow.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Entity
@Table(name = "notebook_qa_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotebookQaHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "qa_history_id")
    private Long qaHistoryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notebook_qa_history_user"))
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notebook_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notebook_qa_history_notebook"))
    private Notebook notebook;

    @Column(name = "user_question", nullable = false, columnDefinition = "text")
    private String userQuestion;

    @Column(name = "ai_answer", nullable = false, columnDefinition = "text")
    private String aiAnswer;

    @Column(name = "answerable", nullable = false)
    private boolean answerable;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "cited_section_ids", columnDefinition = "bigint[]")
    private List<Long> citedSectionIds;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private NotebookQaHistory(
            UserAccount user,
            Notebook notebook,
            String userQuestion,
            String aiAnswer,
            boolean answerable,
            List<Long> citedSectionIds
    ) {
        this.user = user;
        this.notebook = notebook;
        this.userQuestion = userQuestion;
        this.aiAnswer = aiAnswer;
        this.answerable = answerable;
        this.citedSectionIds = citedSectionIds == null || citedSectionIds.isEmpty()
                ? null
                : List.copyOf(citedSectionIds);
    }

    public static NotebookQaHistory create(
            UserAccount user,
            Notebook notebook,
            String userQuestion,
            String aiAnswer,
            boolean answerable,
            List<Long> citedSectionIds
    ) {
        return new NotebookQaHistory(user, notebook, userQuestion, aiAnswer, answerable, citedSectionIds);
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
