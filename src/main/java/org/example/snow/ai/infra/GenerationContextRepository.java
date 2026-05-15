package org.example.snow.ai.infra;

import org.example.snow.ai.domain.GenerationContext;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GenerationContextRepository extends JpaRepository<GenerationContext, Long> {
}
