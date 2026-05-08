package org.example.snow.ai.application;

import lombok.RequiredArgsConstructor;
import org.example.snow.ai.domain.Embedding;
import org.example.snow.ai.infra.EmbeddingRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingRepository embeddingRepository;
    private final JdbcTemplate jdbcTemplate;

    public void saveEmbedding(String text) {

        // ✅ 1. 임베딩 벡터 생성 (지금은 테스트용 랜덤값)
        float[] vector = createDummyEmbedding();

        // ✅ 2. float[] → PostgreSQL vector 문자열로 변환
        String vectorString = convertToPgVector(vector);

        // ✅ 3. JDBC로 직접 INSERT (핵심!!)
        String sql = "INSERT INTO embeddings (text, embedding) VALUES (?, ?::vector)";
        jdbcTemplate.update(sql, text, vectorString);
    }

    // 🔹 더미 임베딩 생성
    private float[] createDummyEmbedding() {
        Random random = new Random();
        float[] vector = new float[1024];

        for (int i = 0; i < 1024; i++) {
            vector[i] = random.nextFloat();
        }

        return vector;
    }

    // 🔹 PostgreSQL vector 형식으로 변환
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