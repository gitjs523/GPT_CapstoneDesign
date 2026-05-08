package org.example.snow.document.domain;

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
@Table(name = "section")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Section {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "section_id")
    private Long sectionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false, foreignKey = @ForeignKey(name = "fk_section_document"))
    private Document document;

    @Column(name = "section_order", nullable = false)
    private int sectionOrder;

    @Column(name = "heading", length = 500)
    private String heading;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "source_start_index", nullable = false)
    private int sourceStartIndex;

    @Column(name = "source_end_index", nullable = false)
    private int sourceEndIndex;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "source_indices", columnDefinition = "integer[]", nullable = false)
    private List<Integer> sourceIndices;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private Section(Document document, ExtractedSection extracted) {
        this.document = document;
        this.sectionOrder = extracted.order();
        this.heading = extracted.heading();
        this.content = extracted.text();
        this.sourceStartIndex = extracted.sourceStartIndex();
        this.sourceEndIndex = extracted.sourceEndIndex();
        this.sourceIndices = List.copyOf(extracted.sourceIndices());
    }

    public static Section create(Document document, ExtractedSection extracted) {
        return new Section(document, extracted);
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
