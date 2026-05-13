package org.example.snow.embedding.infra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Disabled("Ollama 서버가 실행 중일 때만 수동으로 실행 (./gradlew test --tests *EmbeddingClientIntegrationTest)")
class EmbeddingClientIntegrationTest {

    private EmbeddingClient embeddingClient;

    @BeforeEach
    void setUp() {
        embeddingClient = new EmbeddingClient();
        ReflectionTestUtils.setField(embeddingClient, "embeddingUrl",
                System.getenv().getOrDefault("OLLAMA_EMBEDDING_URL", "http://localhost:11434"));
        ReflectionTestUtils.setField(embeddingClient, "embeddingModel",
                System.getenv().getOrDefault("OLLAMA_EMBEDDING_MODEL", "qwen3-embedding:0.6b"));
    }

    @Test
    void embed_단일_텍스트에_대해_1024차원_벡터를_반환한다() {
        float[] vector = embeddingClient.embed("운영체제란 무엇인가?");

        assertThat(vector).hasSize(1024);
        assertThat(hasNonZeroValues(vector)).isTrue();
    }

    @Test
    void embedAll_텍스트_개수만큼_벡터를_반환한다() {
        List<String> texts = List.of(
                "운영체제란 무엇인가?",
                "데이터베이스 정규화 개념",
                "TCP/IP 프로토콜 구조"
        );

        List<float[]> vectors = embeddingClient.embedAll(texts);

        assertThat(vectors).hasSize(3);
        for (float[] vector : vectors) {
            assertThat(vector).hasSize(1024);
            assertThat(hasNonZeroValues(vector)).isTrue();
        }
    }

    @Test
    void embedAll_배치크기보다_많은_텍스트도_모두_임베딩한다() {
        List<String> texts = List.of(
                "프로세스 스케줄링", "메모리 페이징", "파일 시스템 구조",
                "CPU 캐시 계층", "가상 메모리 개념", "인터럽트 처리 방식",
                "세마포어와 뮤텍스", "데드락 조건", "페이지 교체 알고리즘",
                "디스크 스케줄링", "소켓 프로그래밍", "TCP 흐름 제어",
                "UDP와 TCP 차이", "HTTP 프로토콜 구조", "DNS 동작 원리",
                "공개키 암호화", "해시 함수 특성", "트랜잭션 ACID",
                "인덱스 B+트리", "정규화 제3정규형", "SQL 조인 종류"
        );

        List<float[]> vectors = embeddingClient.embedAll(texts);

        assertThat(vectors).hasSize(texts.size());
        vectors.forEach(v -> assertThat(v).hasSize(1024));
    }

    @Test
    void embed_유사한_텍스트가_관련없는_텍스트보다_코사인_거리가_가깝다() {
        float[] os1 = embeddingClient.embed("운영체제는 프로세스를 관리한다");
        float[] os2 = embeddingClient.embed("운영체제는 메모리를 관리한다");
        float[] unrelated = embeddingClient.embed("파스타 요리 레시피");

        double similarDistance = cosineDistance(os1, os2);
        double unrelatedDistance = cosineDistance(os1, unrelated);

        assertThat(similarDistance).isLessThan(unrelatedDistance);
    }

    @Test
    void embed_빈_문자열이면_예외가_발생한다() {
        assertThatThrownBy(() -> embeddingClient.embed(""))
                .isInstanceOf(Exception.class);
    }

    private boolean hasNonZeroValues(float[] vector) {
        for (float v : vector) {
            if (v != 0.0f) return true;
        }
        return false;
    }

    private double cosineDistance(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        return 1.0 - (dot / (Math.sqrt(normA) * Math.sqrt(normB)));
    }
}
