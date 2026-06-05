package org.example.snow.embedding.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.snow.embedding.infra.EmbeddingClient;
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

    /**
     * 블록/텍스트 목록을 임베딩한다 (배치 호출). semantic 분할과 chunk 저장에 공통으로 쓰는 단일 패스.
     * breadcrumb 없이 원문을 그대로 임베딩한다 — semantic 분할이 개념 단위를 보장하므로 blocks의
     * raw 임베딩을 chunk 임베딩으로 그대로 저장한다(분할용/저장용 임베딩 일원화, 비용 1패스).
     */
    public List<float[]> embedAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        log.info("===== embedAll START | total={} batchSize={} =====", texts.size(), batchSize);
        List<float[]> vectors = new java.util.ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += batchSize) {
            List<String> batch = texts.subList(i, Math.min(i + batchSize, texts.size()));
            vectors.addAll(embeddingClient.embedAll(batch));
            log.info("batch {}/{} embedded", Math.min(i + batchSize, texts.size()), texts.size());
        }
        log.info("===== embedAll END =====");
        return vectors;
    }

    public float[] createEmbedding(String text) {
        if (text == null || text.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return embeddingClient.embed(text.trim());
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
