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
import org.example.snow.document.domain.Section;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "generation_context")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GenerationContext {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "context_id")
    private Long contextId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false, foreignKey = @ForeignKey(name = "fk_generation_context_job"))
    private GenerationJob generationJob;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false, foreignKey = @ForeignKey(name = "fk_generation_context_section"))
    private Section section;

    @Column(name = "rank")
    private Integer rank;

    @Column(name = "similarity_score")
    private BigDecimal similarityScore;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private GenerationContext(GenerationJob generationJob, Section section, Integer rank, Double similarityScore) {
        this.generationJob = generationJob;
        this.section = section;
        this.rank = rank;
        this.similarityScore = similarityScore == null ? null : BigDecimal.valueOf(similarityScore);
    }

    public static GenerationContext create(
            GenerationJob generationJob,
            Section section,
            Integer rank,
            Double similarityScore
    ) {
        return new GenerationContext(generationJob, section, rank, similarityScore);
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
