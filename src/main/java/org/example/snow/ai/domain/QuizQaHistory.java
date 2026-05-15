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
import org.example.snow.user.domain.UserAccount;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "quiz_qa_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizQaHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "qa_history_id")
    private Long qaHistoryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_quiz_qa_history_user"))
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false, foreignKey = @ForeignKey(name = "fk_quiz_qa_history_quiz"))
    private GeneratedQuiz quiz;

    @Column(name = "user_question", nullable = false, columnDefinition = "text")
    private String userQuestion;

    @Column(name = "ai_answer", nullable = false, columnDefinition = "text")
    private String aiAnswer;

    @Column(name = "answerable", nullable = false)
    private boolean answerable;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private QuizQaHistory(
            UserAccount user,
            GeneratedQuiz quiz,
            String userQuestion,
            String aiAnswer,
            boolean answerable
    ) {
        this.user = user;
        this.quiz = quiz;
        this.userQuestion = userQuestion;
        this.aiAnswer = aiAnswer;
        this.answerable = answerable;
    }

    public static QuizQaHistory create(
            UserAccount user,
            GeneratedQuiz quiz,
            String userQuestion,
            String aiAnswer,
            boolean answerable
    ) {
        return new QuizQaHistory(user, quiz, userQuestion, aiAnswer, answerable);
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
