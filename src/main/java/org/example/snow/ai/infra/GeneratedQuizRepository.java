package org.example.snow.ai.infra;

import org.example.snow.ai.domain.GeneratedQuiz;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GeneratedQuizRepository extends JpaRepository<GeneratedQuiz, Long> {

    Optional<GeneratedQuiz> findByQuizIdAndDeletedAtIsNull(Long quizId);

    List<GeneratedQuiz> findAllByGenerationJob_JobIdAndDeletedAtIsNullOrderByQuizOrderAsc(Long jobId);
}
