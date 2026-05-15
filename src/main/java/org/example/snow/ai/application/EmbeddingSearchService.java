package org.example.snow.ai.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.snow.embedding.application.EmbeddingService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingSearchService {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;

    private static final int DEFAULT_SEARCH_LIMIT = 5;

    public List<Map<String, Object>> searchSimilarChunks(String question) {

        log.info("===== SEARCH START =====");

        try {
            if (!StringUtils.hasText(question)) {
                throw new IllegalArgumentException("question is empty");
            }

            float[] questionVector = embeddingService.createEmbedding(question.trim());

            String vectorString = convertToPgVector(questionVector);

            log.info("query embedding created");

            String sql = """
                SELECT
                    chunk_id,
                    content,
                    section_id,
                    embedding <=> ?::vector AS distance
                FROM chunk
                WHERE deleted_at IS NULL
                  AND embedding IS NOT NULL
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
            log.error("Embedding search error", e);
            throw e;
        }
    }

    public List<RetrievedSection> searchSimilarSections(Long notebookId, String question) {
        return searchSimilarSections(notebookId, question, DEFAULT_SEARCH_LIMIT);
    }

    public List<RetrievedSection> searchSimilarSections(Long notebookId, String question, int limit) {
        if (notebookId == null) {
            throw new IllegalArgumentException("notebookId is required");
        }
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("question is empty");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }

        float[] questionVector = embeddingService.createEmbedding(question.trim());
        String vectorString = convertToPgVector(questionVector);

        String sql = """
                WITH ranked_sections AS (
                    SELECT
                        s.section_id,
                        s.heading,
                        s.content AS section_content,
                        d.original_file_name AS document_name,
                        s.source_start_index,
                        s.source_end_index,
                        c.embedding <=> ?::vector AS distance,
                        ROW_NUMBER() OVER (
                            PARTITION BY s.section_id
                            ORDER BY c.embedding <=> ?::vector
                        ) AS section_rank
                    FROM chunk c
                    JOIN section s ON s.section_id = c.section_id
                    JOIN document d ON d.document_id = c.document_id
                    WHERE d.notebook_id = ?
                      AND d.analysis_status = 'COMPLETED'
                      AND d.deleted_at IS NULL
                      AND s.deleted_at IS NULL
                      AND c.deleted_at IS NULL
                      AND c.embedding IS NOT NULL
                )
                SELECT
                    section_id,
                    heading,
                    section_content,
                    document_name,
                    source_start_index,
                    source_end_index,
                    distance
                FROM ranked_sections
                WHERE section_rank = 1
                ORDER BY distance
                LIMIT ?
                """;

        List<RetrievedSection> sections = jdbcTemplate.query(
                sql,
                retrievedSectionRowMapper(),
                vectorString,
                vectorString,
                notebookId,
                limit
        );

        log.info("notebookId={} question retrieval result size={}", notebookId, sections.size());
        return sections;
    }

    private RowMapper<RetrievedSection> retrievedSectionRowMapper() {
        return (rs, rowNum) -> {
            double distance = rs.getDouble("distance");
            double similarityScore = Math.max(0.0, Math.min(1.0, 1.0 - distance));
            return new RetrievedSection(
                    Long.toString(rs.getLong("section_id")),
                    rs.getString("heading"),
                    rs.getString("section_content"),
                    rs.getString("document_name"),
                    rs.getObject("source_start_index", Integer.class),
                    rs.getObject("source_end_index", Integer.class),
                    rowNum + 1,
                    similarityScore
            );
        };
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
