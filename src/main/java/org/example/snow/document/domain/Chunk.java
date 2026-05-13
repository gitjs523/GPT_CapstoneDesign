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
@Table(name = "chunk")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Chunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chunk_id")
    private Long chunkId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false, foreignKey = @ForeignKey(name = "fk_chunk_section"))
    private Section section;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false, foreignKey = @ForeignKey(name = "fk_chunk_document"))
    private Document document;

    @Column(name = "chunk_order", nullable = false)
    private int chunkOrder;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "source_start_index")
    private Integer sourceStartIndex;

    @Column(name = "source_end_index")
    private Integer sourceEndIndex;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "source_indices", columnDefinition = "integer[]")
    private List<Integer> sourceIndices;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(name = "embedding", columnDefinition = "vector(1024)")
    private float[] embedding;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private Chunk(Section section, Document document, ExtractedChunk extracted) {
        this.section = section;
        this.document = document;
        this.chunkOrder = extracted.order();
        this.content = extracted.text();
        this.sourceStartIndex = extracted.sourceStartIndex();
        this.sourceEndIndex = extracted.sourceEndIndex();
        this.sourceIndices = extracted.sourceIndices().isEmpty() ? null : List.copyOf(extracted.sourceIndices());
    }

    public static Chunk create(Section section, Document document, ExtractedChunk extracted) {
        return new Chunk(section, document, extracted);
    }

    public void updateEmbedding(float[] embedding) {
        this.embedding = embedding;
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
