package org.example.snow.ai.infra;

import org.example.snow.ai.domain.GenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, Long> {

    Optional<GenerationJob> findByJobIdAndDeletedAtIsNull(Long jobId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE GenerationJob j SET j.deletedAt = :now WHERE j.notebook.notebookId = :notebookId AND j.deletedAt IS NULL")
    void softDeleteByNotebookId(@Param("notebookId") Long notebookId, @Param("now") LocalDateTime now);
}
