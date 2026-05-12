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

    public List<String> searchSimilarChunks(String query) {

        log.info("===== SEARCH START =====");
        log.info("query = {}", query);

        // 1. 질문 embedding 생성 (재사용)
        float[] queryVector = embeddingService.requestEmbedding(query);

        String vectorString = convertToPgVector(queryVector);

        // 2. pgvector 유사도 검색
        String sql = """
                SELECT content
                FROM chunk
                ORDER BY embedding <-> ?::vector
                LIMIT 5
                """;

        List<String> results = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getString("content"),
                vectorString
        );

        log.info("results size = {}", results.size());
        log.info("===== SEARCH END =====");

        return results;
    }

    private String convertToPgVector(float[] vector) {

        StringBuilder sb = new StringBuilder("[");

        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);

            if (i != vector.length - 1) {
                sb.append(",");
            }
        }

        sb.append("]");

        return sb.toString();
    }
}