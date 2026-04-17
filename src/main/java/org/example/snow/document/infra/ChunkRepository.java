package org.example.snow.document.infra;

import org.example.snow.document.domain.Chunk;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChunkRepository extends JpaRepository<Chunk, Long> {
}
