package org.example.snow.ai.application;

import lombok.RequiredArgsConstructor;
import org.example.snow.ai.domain.Embedding;
import org.example.snow.ai.infra.EmbeddingRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingRepository embeddingRepository;

    // 테스트용 임베딩 생성 (나중에 OpenAI/Ollama로 교체)
    public float[] createEmbedding(String text) {
        float[] vector = new float[1024];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) Math.random();
        }
        return vector;
    }

    public void saveEmbedding(String text) {
        float[] embeddingVector = createEmbedding(text);
        Embedding embedding = new Embedding(text, embeddingVector);
        embeddingRepository.save(embedding);
    }
}