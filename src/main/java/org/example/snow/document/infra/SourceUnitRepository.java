package org.example.snow.document.infra;

import org.example.snow.document.domain.SourceUnit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceUnitRepository extends JpaRepository<SourceUnit, Long> {
}
