package org.example.snow.ai.infra;

import org.example.snow.ai.domain.NotebookQaHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotebookQaHistoryRepository extends JpaRepository<NotebookQaHistory, Long> {

    List<NotebookQaHistory> findAllByNotebook_NotebookIdAndUser_UserIdAndDeletedAtIsNullOrderByCreatedAtAsc(
            Long notebookId,
            Long userId
    );

    @Modifying(clearAutomatically = true)
    @Query("UPDATE NotebookQaHistory h SET h.deletedAt = :now WHERE h.notebook.notebookId = :notebookId AND h.deletedAt IS NULL")
    void softDeleteByNotebookId(@Param("notebookId") Long notebookId, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE notebook_qa_history SET cited_section_ids = NULL
            WHERE cited_section_ids IS NOT NULL
              AND EXISTS (
                  SELECT 1 FROM section s
                  WHERE s.document_id = :documentId
                    AND s.section_id = ANY(notebook_qa_history.cited_section_ids)
              )
            """, nativeQuery = true)
    void clearCitedSectionIdsByDocumentId(@Param("documentId") Long documentId);
}
