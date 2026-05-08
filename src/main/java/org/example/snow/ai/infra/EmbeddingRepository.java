package org.example.snow.ai.infra;

import org.example.snow.ai.domain.Embedding;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmbeddingRepository extends JpaRepository<Embedding, Long> {
}