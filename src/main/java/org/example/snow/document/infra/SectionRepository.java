package org.example.snow.document.infra;

import org.example.snow.document.domain.AnalysisStatus;
import org.example.snow.document.domain.Section;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface SectionRepository extends JpaRepository<Section, Long> {

    List<Section> findAllByDocument_DocumentIdAndDeletedAtIsNullOrderBySectionOrderAsc(Long documentId);

    List<Section> findAllBySectionIdInAndDeletedAtIsNull(Collection<Long> sectionIds);

    /** 노트북 내 해당 상태(보통 COMPLETED)·미삭제 문서에 속한 미삭제 Section 수. 문제 생성 상한 계산용. */
    @Query("SELECT COUNT(s) FROM Section s "
            + "WHERE s.document.notebook.notebookId = :notebookId "
            + "AND s.document.analysisStatus = :status "
            + "AND s.document.deletedAt IS NULL "
            + "AND s.deletedAt IS NULL")
    long countByNotebookAndDocumentStatus(@Param("notebookId") Long notebookId, @Param("status") AnalysisStatus status);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Section s SET s.deletedAt = :now WHERE s.document.documentId = :documentId AND s.deletedAt IS NULL")
    void softDeleteByDocumentId(@Param("documentId") Long documentId, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Section s SET s.deletedAt = :now WHERE s.document.notebook.notebookId = :notebookId AND s.deletedAt IS NULL")
    void softDeleteByNotebookId(@Param("notebookId") Long notebookId, @Param("now") LocalDateTime now);
}
