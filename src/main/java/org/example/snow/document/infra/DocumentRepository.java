package org.example.snow.document.infra;

import org.example.snow.document.domain.AnalysisStatus;
import org.example.snow.document.domain.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findAllByNotebook_NotebookIdAndDeletedAtIsNullOrderByUploadedAtAsc(Long notebookId);

    Optional<Document> findByDocumentIdAndDeletedAtIsNull(Long documentId);

    boolean existsByNotebook_NotebookIdAndAnalysisStatusAndDeletedAtIsNull(Long notebookId, AnalysisStatus analysisStatus);

    boolean existsByNotebook_User_UserIdAndAnalysisStatusAndDeletedAtIsNull(Long userId, AnalysisStatus analysisStatus);

    boolean existsByNotebook_NotebookIdAndAnalysisStatusInAndDeletedAtIsNull(Long notebookId, Collection<AnalysisStatus> statuses);

    boolean existsByNotebook_User_UserIdAndAnalysisStatusInAndDeletedAtIsNull(Long userId, Collection<AnalysisStatus> statuses);

    List<Document> findAllByAnalysisStatusAndDeletedAtIsNull(AnalysisStatus analysisStatus);

    List<Document> findAllByAnalysisStatusInAndDeletedAtIsNull(Collection<AnalysisStatus> statuses);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Document d SET d.deletedAt = :now WHERE d.notebook.notebookId = :notebookId AND d.deletedAt IS NULL")
    void softDeleteByNotebookId(@Param("notebookId") Long notebookId, @Param("now") LocalDateTime now);
}
