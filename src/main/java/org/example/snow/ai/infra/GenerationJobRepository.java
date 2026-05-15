package org.example.snow.ai.infra;

import org.example.snow.ai.domain.GenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, Long> {

    Optional<GenerationJob> findByJobIdAndDeletedAtIsNull(Long jobId);
}
