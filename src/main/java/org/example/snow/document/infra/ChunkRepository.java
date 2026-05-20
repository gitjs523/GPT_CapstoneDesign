package org.example.snow.document.infra;

import org.example.snow.document.domain.Chunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChunkRepository extends JpaRepository<Chunk, Long> {

    List<Chunk> findByDocument_DocumentId(Long documentId);

    @Query(value = """
            SELECT c.chunk_id, c.content, c.section_id,
                   c.embedding <=> CAST(:embedding AS vector) AS distance
            FROM chunk c
            JOIN section s ON c.section_id = s.section_id
            JOIN document d ON c.document_id = d.document_id
            WHERE c.deleted_at IS NULL
              AND s.deleted_at IS NULL
              AND d.deleted_at IS NULL
              AND d.analysis_status = 'COMPLETED'
              AND d.notebook_id = :notebookId
            ORDER BY distance
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findTopSimilarChunks(
            @Param("embedding") String embedding,
            @Param("notebookId") Long notebookId,
            @Param("limit") int limit
    );

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Chunk c SET c.deletedAt = :now WHERE c.document.documentId = :documentId AND c.deletedAt IS NULL")
    void softDeleteByDocumentId(@Param("documentId") Long documentId, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Chunk c SET c.deletedAt = :now WHERE c.document.notebook.notebookId = :notebookId AND c.deletedAt IS NULL")
    void softDeleteByNotebookId(@Param("notebookId") Long notebookId, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Chunk c SET c.embedding = null WHERE c.document.documentId = :documentId AND c.deletedAt IS NULL")
    void nullEmbeddingByDocumentId(@Param("documentId") Long documentId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Chunk c SET c.embedding = null WHERE c.document.notebook.notebookId = :notebookId AND c.deletedAt IS NULL")
    void nullEmbeddingByNotebookId(@Param("notebookId") Long notebookId);
}
