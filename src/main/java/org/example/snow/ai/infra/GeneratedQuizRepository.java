package org.example.snow.ai.infra;

import org.example.snow.ai.domain.GeneratedQuiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GeneratedQuizRepository extends JpaRepository<GeneratedQuiz, Long> {

    Optional<GeneratedQuiz> findByQuizIdAndDeletedAtIsNull(Long quizId);

    List<GeneratedQuiz> findAllByGenerationJob_JobIdAndDeletedAtIsNullOrderByQuizOrderAsc(Long jobId);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE generated_quiz SET deleted_at = :now
            WHERE deleted_at IS NULL
              AND job_id IN (SELECT job_id FROM generation_job WHERE notebook_id = :notebookId)
            """, nativeQuery = true)
    void softDeleteByNotebookId(@Param("notebookId") Long notebookId, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE generated_quiz SET source_section_ids = NULL
            WHERE source_section_ids IS NOT NULL
              AND EXISTS (
                  SELECT 1 FROM section s
                  WHERE s.document_id = :documentId
                    AND s.section_id = ANY(generated_quiz.source_section_ids)
              )
            """, nativeQuery = true)
    void clearSourceSectionIdsByDocumentId(@Param("documentId") Long documentId);
}
