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

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Chunk c SET c.deletedAt = :now WHERE c.document.documentId = :documentId AND c.deletedAt IS NULL")
    void softDeleteByDocumentId(@Param("documentId") Long documentId, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Chunk c SET c.deletedAt = :now WHERE c.document.notebook.notebookId = :notebookId AND c.deletedAt IS NULL")
    void softDeleteByNotebookId(@Param("notebookId") Long notebookId, @Param("now") LocalDateTime now);
}
