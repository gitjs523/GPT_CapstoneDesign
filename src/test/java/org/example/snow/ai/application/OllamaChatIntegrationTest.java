package org.example.snow.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// 수동 실행 시: @Disabled를 임시 제거 후 ./gradlew test --tests "*OllamaChatIntegrationTest" 실행
// (--tests 플래그만으로는 @Disabled가 무시되지 않음)
@Disabled("Ollama 생성 모델 서버가 실행 중일 때만 수동으로 실행")
class OllamaChatIntegrationTest {

    private static final int TIMEOUT_SECONDS = 120;

    private OllamaService ollamaService;
    private String chatUrl;
    private String chatModel;

    @BeforeEach
    void setUp() {
        chatUrl = System.getenv().getOrDefault("OLLAMA_CHAT_URL", "http://localhost:11434");
        chatModel = System.getenv().getOrDefault("OLLAMA_CHAT_MODEL", "qwen3:4b-q4_K_M");

        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(chatUrl)
                .restClientBuilder(RestClient.builder())
                .build();

        OllamaChatModel chatModelBean = OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(OllamaChatOptions.builder().model(chatModel).build())
                .build();

        ChatClient.Builder chatClientBuilder = ChatClient.builder(chatModelBean);
        ollamaService = new OllamaService(chatClientBuilder, new ObjectMapper());

        System.out.println("=== 생성 모델 연결 대상 ===");
        System.out.println("URL  : " + chatUrl);
        System.out.println("Model: " + chatModel);
    }

    // ─── 기본 연결 확인 ──────────────────────────────────────────────────

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void ask_단순_질문에_응답을_반환한다() {
        String answer = ollamaService.ask("운영체제가 뭐야? 한 문장으로만 답해줘.");

        System.out.println("[ask] 응답: " + answer);
        assertThat(answer).isNotBlank();
    }

    // ─── 요약 생성 확인 ───────────────────────────────────────────────────

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void generateSummary_문서_내용을_요약해서_반환한다() {
        String content = """
                1. 프로세스 관리
                운영체제는 CPU를 여러 프로세스에 할당하는 스케줄링을 담당한다.
                프로세스는 실행 중인 프로그램을 의미하며, PCB(Process Control Block)에 상태가 저장된다.
                컨텍스트 스위칭은 실행 중인 프로세스를 교체하는 작업이다.

                2. 메모리 관리
                가상 메모리는 실제 메모리보다 큰 주소 공간을 프로세스에 제공한다.
                페이징은 메모리를 고정 크기의 페이지로 나누어 관리하는 방식이다.
                페이지 폴트가 발생하면 디스크에서 페이지를 메모리로 로드한다.
                """;

        String summary = ollamaService.generateSummary(content);

        System.out.println("[generateSummary] 응답:\n" + summary);
        assertThat(summary).isNotBlank();
        assertThat(summary.length()).isGreaterThan(10);
    }

    // ─── Grounded 답변 생성 확인 ─────────────────────────────────────────

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void generateGroundedAnswer_검색된_섹션_기반으로_JSON_응답을_반환한다() {
        List<RetrievedSection> sections = List.of(
                new RetrievedSection(
                        "101",
                        "1. 프로세스 관리",
                        "운영체제는 CPU를 여러 프로세스에 할당하는 스케줄링을 담당한다. "
                                + "컨텍스트 스위칭은 실행 중인 프로세스를 교체하는 작업이다.",
                        "os_lecture.pdf",
                        1, 1, 1, 0.95
                )
        );

        GeneratedAnswer answer = ollamaService.generateGroundedAnswer(
                new AnswerGenerationCommand("컨텍스트 스위칭이란?", sections)
        );

        System.out.println("[generateGroundedAnswer] answerable : " + answer.answerable());
        System.out.println("[generateGroundedAnswer] answer     : " + answer.answer());
        System.out.println("[generateGroundedAnswer] citedIds   : " + answer.citedSectionIds());
        assertThat(answer.answer()).isNotBlank();
        assertThat(answer.answerable()).isTrue();
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void generateGroundedAnswer_관련없는_질문에는_answerable_false를_반환한다() {
        List<RetrievedSection> sections = List.of(
                new RetrievedSection(
                        "101",
                        "1. 프로세스 관리",
                        "운영체제는 CPU를 여러 프로세스에 할당하는 스케줄링을 담당한다.",
                        "os_lecture.pdf",
                        1, 1, 1, 0.95
                )
        );

        GeneratedAnswer answer = ollamaService.generateGroundedAnswer(
                new AnswerGenerationCommand("파스타 요리 방법을 알려줘", sections)
        );

        System.out.println("[generateGroundedAnswer - unrelated] answerable : " + answer.answerable());
        System.out.println("[generateGroundedAnswer - unrelated] answer     : " + answer.answer());
        assertThat(answer.answer()).isNotBlank();
        assertThat(answer.answerable()).isFalse();
    }
}
