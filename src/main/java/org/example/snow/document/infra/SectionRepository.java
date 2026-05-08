package org.example.snow.document.infra;

import org.example.snow.document.domain.Section;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SectionRepository extends JpaRepository<Section, Long> {

    List<Section> findAllByDocument_DocumentIdAndDeletedAtIsNullOrderBySectionOrderAsc(Long documentId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Section s SET s.deletedAt = :now WHERE s.document.documentId = :documentId AND s.deletedAt IS NULL")
    void softDeleteByDocumentId(@Param("documentId") Long documentId, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Section s SET s.deletedAt = :now WHERE s.document.notebook.notebookId = :notebookId AND s.deletedAt IS NULL")
    void softDeleteByNotebookId(@Param("notebookId") Long notebookId, @Param("now") LocalDateTime now);
}
