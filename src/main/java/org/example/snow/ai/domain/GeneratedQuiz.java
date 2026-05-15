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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Entity
@Table(name = "generated_quiz")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GeneratedQuiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "quiz_id")
    private Long quizId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false, foreignKey = @ForeignKey(name = "fk_generated_question_job"))
    private GenerationJob generationJob;

    @Column(name = "quiz_order")
    private Integer quizOrder;

    @Column(name = "quiz_type", length = 20)
    private String quizType;

    @Column(name = "question_text", columnDefinition = "text")
    private String questionText;

    @Column(name = "choices", columnDefinition = "text")
    private String choices;

    @Column(name = "answer", columnDefinition = "text")
    private String answer;

    @Column(name = "explanation", columnDefinition = "text")
    private String explanation;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "source_section_ids", columnDefinition = "bigint[]")
    private List<Long> sourceSectionIds;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private GeneratedQuiz(
            GenerationJob generationJob,
            Integer quizOrder,
            String quizType,
            String questionText,
            String choices,
            String answer,
            String explanation,
            List<Long> sourceSectionIds
    ) {
        this.generationJob = generationJob;
        this.quizOrder = quizOrder;
        this.quizType = quizType;
        this.questionText = questionText;
        this.choices = choices;
        this.answer = answer;
        this.explanation = explanation;
        this.sourceSectionIds = sourceSectionIds == null || sourceSectionIds.isEmpty()
                ? null
                : List.copyOf(sourceSectionIds);
    }

    public static GeneratedQuiz create(
            GenerationJob generationJob,
            Integer quizOrder,
            String quizType,
            String questionText,
            String choices,
            String answer,
            String explanation,
            List<Long> sourceSectionIds
    ) {
        return new GeneratedQuiz(
                generationJob,
                quizOrder,
                quizType,
                questionText,
                choices,
                answer,
                explanation,
                sourceSectionIds
        );
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
