package org.example.snow.embedding.infra;

import org.example.snow.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EmbeddingClient의 재시도 동작 단위 테스트.
 *
 * Spring 컨텍스트/실서버 없이 RestTemplate을 Mockito로 대체해 검증한다
 * (restTemplate 필드를 직접 주입해 lazy 생성을 우회). RDS와 무관하다.
 */
class EmbeddingClientRetryTest {

    private EmbeddingClient clientWith(RestTemplate restTemplate, int maxAttempts) {
        EmbeddingClient client = new EmbeddingClient();
        ReflectionTestUtils.setField(client, "embeddingUrl", "http://embed.test");
        ReflectionTestUtils.setField(client, "embeddingModel", "test-model");
        ReflectionTestUtils.setField(client, "maxAttempts", maxAttempts);
        ReflectionTestUtils.setField(client, "retryBackoffMs", 1L);
        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
        return client;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ResponseEntity<Map> successResponse() {
        Map<String, Object> body = Map.of("embeddings", List.of(List.of(0.1, 0.2, 0.3)));
        return (ResponseEntity) ResponseEntity.ok(body);
    }

    @Test
    void 일시적_timeout_후_재시도하여_성공한다() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("Read timed out"))
                .thenReturn(successResponse());

        EmbeddingClient client = clientWith(restTemplate, 3);

        float[] vector = client.embed("hello");

        assertThat(vector).hasSize(3);
        verify(restTemplate, times(2)).postForEntity(anyString(), any(), eq(Map.class));
    }

    @Test
    void 모든_재시도가_실패하면_BusinessException을_던진다() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("Read timed out"));

        EmbeddingClient client = clientWith(restTemplate, 3);

        assertThatThrownBy(() -> client.embed("hello"))
                .isInstanceOf(BusinessException.class);
        verify(restTemplate, times(3)).postForEntity(anyString(), any(), eq(Map.class));
    }

    @Test
    void 응답_포맷_오류는_재시도하지_않고_즉시_실패한다() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        // "embeddings" 키가 없는 정상 200 응답 — 결정적 오류이므로 재시도 대상이 아니다.
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("unexpected", "shape")));

        EmbeddingClient client = clientWith(restTemplate, 3);

        assertThatThrownBy(() -> client.embed("hello"))
                .isInstanceOf(BusinessException.class);
        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(Map.class));
    }
}
