package org.example.snow.ai.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.example.snow.document.domain.Chunk;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final JdbcTemplate jdbcTemplate;

    private final RestTemplate restTemplate = new RestTemplate();

    public void saveEmbedding(Chunk chunk) {

        log.info("===== saveEmbedding START =====");

        try {

            if (chunk == null) {
                log.error("chunk is null");
                return;
            }

            log.info("chunkId = {}", chunk.getChunkId());
            log.info("content = {}", chunk.getContent());

            float[] vector = requestEmbedding(chunk.getContent());

            String vectorString = convertToPgVector(vector);

            log.info("before update");

            String sql =
                    "UPDATE chunk SET embedding = ?::vector WHERE chunk_id = ?";

            int result = jdbcTemplate.update(
                    sql,
                    vectorString,
                    chunk.getChunkId()
            );

            log.info("after update");
            log.info("update result = {}", result);

        } catch (Exception e) {

            log.error("❌ Embedding DB error occurred", e);

            throw e;
        }

        log.info("===== saveEmbedding END =====");
    }

    public float[] requestEmbedding(String text) {

        String url =
                "http://host.docker.internal:11434/api/embeddings";

        HttpHeaders headers = new HttpHeaders();

        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "model", "qwen3-embedding:0.6b",
                "prompt", text
        );

        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(body, headers);

        ResponseEntity<Map> response =
                restTemplate.postForEntity(
                        url,
                        request,
                        Map.class
                );

        List<Double> embeddingList =
                (List<Double>) response.getBody().get("embedding");

        float[] vector = new float[embeddingList.size()];

        for (int i = 0; i < embeddingList.size(); i++) {

            vector[i] =
                    embeddingList.get(i).floatValue();
        }

        return vector;
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