package org.example.snow.embedding.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.snow.embedding.infra.EmbeddingClient;
import org.example.snow.document.domain.Chunk;
import org.example.snow.document.infra.ChunkRepository;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.example.snow.notebook.infra.NotebookRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingClient embeddingClient;
    private final ChunkRepository chunkRepository;
    private final NotebookRepository notebookRepository;

    @Value("${ollama.embedding.batch-size}")
    private int batchSize;

    public void saveEmbeddings(List<Chunk> chunks) {
        log.info("===== saveEmbeddings START | total={} batchSize={} =====", chunks.size(), batchSize);

        for (int i = 0; i < chunks.size(); i += batchSize) {
            List<Chunk> batch = chunks.subList(i, Math.min(i + batchSize, chunks.size()));

            List<String> texts = batch.stream()
                    .map(Chunk::getContent)
                    .toList();

            List<float[]> vectors = embeddingClient.embedAll(texts);

            for (int j = 0; j < batch.size(); j++) {
                batch.get(j).updateEmbedding(vectors.get(j));
            }

            chunkRepository.saveAll(batch);

            log.info("batch {}/{} saved", Math.min(i + batchSize, chunks.size()), chunks.size());
        }

        log.info("===== saveEmbeddings END =====");
    }

    public List<SimilarChunk> searchSimilarChunks(String question, Long notebookId, Long userId, int topK) {
        log.info("===== SEARCH START =====");

        if (question == null || question.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        var notebook = notebookRepository.findByNotebookIdAndDeletedAtIsNull(notebookId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTEBOOK_NOT_FOUND));
        if (!notebook.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOTEBOOK_ACCESS_DENIED);
        }

        String vectorString = convertToPgVector(embeddingClient.embed(question));

        log.info("query embedding created");

        List<Object[]> rows = chunkRepository.findTopSimilarChunks(vectorString, notebookId, topK);

        List<SimilarChunk> results = rows.stream()
                .map(row -> new SimilarChunk(
                        ((Number) row[0]).longValue(),
                        (String) row[1],
                        ((Number) row[2]).longValue(),
                        ((Number) row[3]).doubleValue()
                ))
                .toList();

        log.info("search result size = {}", results.size());
        log.info("===== SEARCH END =====");

        return results;
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
