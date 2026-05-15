package org.example.snow.ai.infra;

import org.example.snow.ai.domain.QuizQaHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizQaHistoryRepository extends JpaRepository<QuizQaHistory, Long> {

    List<QuizQaHistory> findAllByQuiz_QuizIdAndUser_UserIdAndDeletedAtIsNullOrderByCreatedAtAsc(
            Long quizId,
            Long userId
    );
}
