package org.example.snow.ai.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingSearchService {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;

    public List<Map<String, Object>> searchSimilarChunks(String question) {

        log.info("===== SEARCH START =====");

        try {
            if (question == null || question.isBlank()) {
                throw new IllegalArgumentException("question is empty");
            }

            float[] questionVector = embeddingService.requestEmbedding(question);

            String vectorString = convertToPgVector(questionVector);

            log.info("vectorString = {}", vectorString);

            log.info("query embedding created");

            String sql = """
                SELECT
                    chunk_id,
                    content,
                    section_id,
                    embedding <=> ?::vector AS distance
                FROM chunk
                ORDER BY distance
                LIMIT 5
            """;

            List<Map<String, Object>> results = jdbcTemplate.queryForList(
                    sql,
                    vectorString
            );

            log.info("search result size = {}", results.size());
            log.info("===== SEARCH END =====");

            return results;

        } catch (Exception e) {
            log.error("❌ Embedding search error", e);
            throw e;
        }
    }
    
    private String convertToPgVector(float[] vector) {
        StringBuilder sb = new StringBuilder("[");

        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i != vector.length - 1) sb.append(",");
        }

        sb.append("]");
        return sb.toString();
    }
}