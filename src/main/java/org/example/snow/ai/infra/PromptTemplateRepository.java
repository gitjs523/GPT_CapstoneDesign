package org.example.snow.ai.infra;

import org.example.snow.ai.domain.PromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, Long> {

    Optional<PromptTemplate> findFirstByIsActiveTrueOrderByCreatedAtDesc();
}
