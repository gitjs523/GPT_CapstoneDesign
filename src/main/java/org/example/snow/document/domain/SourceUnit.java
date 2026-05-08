package org.example.snow.document.domain;

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

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "source_unit")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SourceUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "source_unit_id")
    private Long sourceUnitId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false, foreignKey = @ForeignKey(name = "fk_source_unit_document"))
    private Document document;

    @Column(name = "unit_index", nullable = false)
    private int unitIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private SourceUnitType sourceType;

    @Column(name = "heading", length = 500)
    private String heading;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private SourceUnit(Document document, ExtractedSourceUnit extracted, SourceUnitType sourceType) {
        this.document = document;
        this.unitIndex = extracted.index();
        this.sourceType = sourceType;
        this.heading = extracted.heading();
        this.content = extracted.text();
    }

    public static SourceUnit create(Document document, ExtractedSourceUnit extracted, SourceUnitType sourceType) {
        return new SourceUnit(document, extracted, sourceType);
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
