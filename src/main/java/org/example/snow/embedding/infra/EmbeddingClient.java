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

    private List<float[]> callEmbedApi(List<String> texts) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "model", embeddingModel,
                "input", texts
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response;
        try {
            response = getRestTemplate().postForEntity(embeddingUrl + "/api/embed", request, Map.class);
        } catch (RestClientException e) {
            log.error("임베딩 모델 HTTP 호출 실패 | url={} model={} cause={}", embeddingUrl, embeddingModel, e.getMessage(), e);
            throw new BusinessException(ErrorCode.EMBEDDING_MODEL_CALL_FAILED);
        }

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

    private float[] toFloatArray(List<Double> values) {
        float[] vector = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            vector[i] = values.get(i).floatValue();
        }
        return vector;
    }
}
