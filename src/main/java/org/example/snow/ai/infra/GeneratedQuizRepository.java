package org.example.snow.ai.infra;

import org.example.snow.ai.domain.GeneratedQuiz;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GeneratedQuizRepository extends JpaRepository<GeneratedQuiz, Long> {

    List<GeneratedQuiz> findAllByGenerationJob_JobIdAndDeletedAtIsNullOrderByQuizOrderAsc(Long jobId);
}
