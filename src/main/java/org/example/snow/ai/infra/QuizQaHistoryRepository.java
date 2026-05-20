package org.example.snow.ai.infra;

import org.example.snow.ai.domain.QuizQaHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface QuizQaHistoryRepository extends JpaRepository<QuizQaHistory, Long> {

    List<QuizQaHistory> findAllByQuiz_QuizIdAndUser_UserIdAndDeletedAtIsNullOrderByCreatedAtAsc(
            Long quizId,
            Long userId
    );

    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE quiz_qa_history SET deleted_at = :now
            WHERE deleted_at IS NULL
              AND quiz_id IN (
                  SELECT q.quiz_id FROM generated_quiz q
                  JOIN generation_job j ON j.job_id = q.job_id
                  WHERE j.notebook_id = :notebookId
              )
            """, nativeQuery = true)
    void softDeleteByNotebookId(@Param("notebookId") Long notebookId, @Param("now") LocalDateTime now);
}
