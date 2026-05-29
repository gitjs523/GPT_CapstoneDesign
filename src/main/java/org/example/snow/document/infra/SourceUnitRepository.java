package org.example.snow.document.infra;

import org.example.snow.document.domain.SourceUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SourceUnitRepository extends JpaRepository<SourceUnit, Long> {

    @Modifying
    @Query("DELETE FROM SourceUnit su WHERE su.document.documentId = :documentId")
    void deleteAllByDocumentId(@Param("documentId") Long documentId);

    @Modifying
    @Query("DELETE FROM SourceUnit su WHERE su.document.notebook.notebookId = :notebookId")
    void deleteAllByNotebookId(@Param("notebookId") Long notebookId);
}
