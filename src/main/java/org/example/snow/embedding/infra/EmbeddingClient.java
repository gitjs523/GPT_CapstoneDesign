package org.example.snow.embedding.infra;

import lombok.extern.slf4j.Slf4j;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class EmbeddingClient {

    @Value("${ollama.embedding.url}")
    private String embeddingUrl;

    @Value("${ollama.embedding.model}")
    private String embeddingModel;

    @Value("${ollama.embedding.connect-timeout-seconds}")
    private int connectTimeoutSeconds;

    @Value("${ollama.embedding.read-timeout-seconds}")
    private int readTimeoutSeconds;

    @Value("${ollama.embedding.max-attempts:3}")
    private int maxAttempts;

    @Value("${ollama.embedding.retry-backoff-ms:1000}")
    private long retryBackoffMs;

    private volatile RestTemplate restTemplate;

    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            synchronized (this) {
                if (restTemplate == null) {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(connectTimeoutSeconds * 1000);
                    factory.setReadTimeout(readTimeoutSeconds * 1000);
                    restTemplate = new RestTemplate(factory);
                }
            }
        }
        return restTemplate;
    }

    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("임베딩 대상 텍스트는 비어 있을 수 없습니다.");
        }
        List<float[]> vectors = callEmbedApi(List.of(text));
        return vectors.get(0);
    }

    public List<float[]> embedAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new IllegalArgumentException("임베딩 대상 텍스트 목록은 비어 있을 수 없습니다.");
        }
        return callEmbedApi(texts);
    }

    /**
     * 임베딩 배치 1건을 호출한다. 일시적 네트워크 실패(timeout 등 {@link RestClientException})는
     * maxAttempts까지 backoff 후 재시도한다 — EC2→ngrok 경로에서 배치 1건이 read-timeout을
     * 스파이크해도 문서 전체가 FAILED되지 않도록 한다. 응답 포맷 오류(키 누락 등)는 결정적
     * 실패이므로 재시도하지 않고 즉시 전파한다.
     */
    private List<float[]> callEmbedApi(List<String> texts) {
        int attempts = Math.max(1, maxAttempts);
        RestClientException lastError = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return requestEmbeddings(texts);
            } catch (RestClientException e) {
                lastError = e;
                log.warn("임베딩 모델 HTTP 호출 실패 (시도 {}/{}) | url={} model={} cause={}",
                        attempt, attempts, embeddingUrl, embeddingModel, e.getMessage());
                if (attempt < attempts) {
                    sleepBeforeRetry(attempt);
                }
            }
        }
        log.error("임베딩 모델 HTTP 호출 최종 실패 (재시도 {}회 소진) | url={} model={}",
                attempts, embeddingUrl, embeddingModel, lastError);
        throw new BusinessException(ErrorCode.EMBEDDING_MODEL_CALL_FAILED, lastError);
    }

    private List<float[]> requestEmbeddings(List<String> texts) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // ngrok 무료 터널의 브라우저 경고 인터스티셜 회피 (OllamaVisionClient와 동일)
        headers.set("ngrok-skip-browser-warning", "1");

        Map<String, Object> body = Map.of(
                "model", embeddingModel,
                "input", texts
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response =
                getRestTemplate().postForEntity(embeddingUrl + "/api/embed", request, Map.class);

        Map<?, ?> responseBody = response.getBody();
        if (responseBody == null) {
            log.error("임베딩 모델 응답 body가 null | url={} model={}", embeddingUrl, embeddingModel);
            throw new BusinessException(ErrorCode.EMBEDDING_MODEL_CALL_FAILED);
        }

        Object embeddingsRaw = responseBody.get("embeddings");
        if (embeddingsRaw == null) {
            log.error("임베딩 모델 응답에 'embeddings' 키 없음 | response={}", responseBody);
            throw new BusinessException(ErrorCode.EMBEDDING_MODEL_CALL_FAILED);
        }

        List<List<Double>> embeddings = (List<List<Double>>) embeddingsRaw;

        if (embeddings.size() != texts.size()) {
            log.error("임베딩 결과 개수 불일치 | 요청={} 응답={}", texts.size(), embeddings.size());
            throw new BusinessException(ErrorCode.EMBEDDING_MODEL_CALL_FAILED);
        }

        List<float[]> result = new ArrayList<>(embeddings.size());
        for (List<Double> embedding : embeddings) {
            result.add(toFloatArray(embedding));
        }
        return result;
    }

    private void sleepBeforeRetry(int attempt) {
        long backoffMs = retryBackoffMs * attempt;
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.EMBEDDING_MODEL_CALL_FAILED, e);
        }
    }

    private float[] toFloatArray(List<Double> values) {
        float[] vector = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            vector[i] = values.get(i).floatValue();
        }
        return vector;
    }
}
