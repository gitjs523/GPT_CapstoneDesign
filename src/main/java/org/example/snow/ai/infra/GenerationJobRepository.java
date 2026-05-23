package org.example.snow.ai.infra;

import org.example.snow.ai.domain.GenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, Long> {

    Optional<GenerationJob> findByJobIdAndDeletedAtIsNull(Long jobId);
    List<GenerationJob> findAllByNotebook_NotebookIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long notebookId);

    @Query("SELECT j FROM GenerationJob j LEFT JOIN FETCH j.notebook LEFT JOIN FETCH j.promptTemplate WHERE j.jobId = :jobId")
    Optional<GenerationJob> findByIdWithDetails(@Param("jobId") Long jobId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE GenerationJob j SET j.deletedAt = :now WHERE j.notebook.notebookId = :notebookId AND j.deletedAt IS NULL")
    void softDeleteByNotebookId(@Param("notebookId") Long notebookId, @Param("now") LocalDateTime now);
}
