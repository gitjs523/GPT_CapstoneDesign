package org.example.snow.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.snow.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OllamaServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void appliesSystemPromptAndReturnsModelContent() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);

        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("운영체제는 하드웨어와 프로그램 사이를 관리합니다.");

        OllamaService ollamaService = new OllamaService(builder, objectMapper);

        String answer = ollamaService.ask(" 운영체제를 설명해줘 ");

        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(systemPrompt.capture());
        verify(requestSpec).user("운영체제를 설명해줘");
        assertThat(systemPrompt.getValue())
                .contains("한국어로만")
                .contains("3문장 이내")
                .contains("마크다운");
        assertThat(answer).isEqualTo("운영체제는 하드웨어와 프로그램 사이를 관리합니다.");
    }

    @Test
    void rejectsBlankQuestion() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        when(builder.build()).thenReturn(chatClient);

        OllamaService ollamaService = new OllamaService(builder, objectMapper);

        assertThatThrownBy(() -> ollamaService.ask("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("질문 q는 필수입니다.");
        verify(chatClient, never()).prompt();
    }

    @Test
    void generatesGroundedAnswerFromRetrievedSections() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);

        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("""
                ```json
                {
                  "answer": "RAG는 검색 결과를 바탕으로 답변 품질을 높이는 방식입니다.",
                  "citedSectionIds": ["sec-1"],
                  "answerable": true
                }
                ```
                """);

        OllamaService ollamaService = new OllamaService(builder, objectMapper);

        GeneratedAnswer generatedAnswer = ollamaService.generateGroundedAnswer(new AnswerGenerationCommand(
                "RAG가 뭐야?",
                java.util.List.of(new RetrievedSection(
                        "sec-1",
                        "1. RAG Overview",
                        "RAG는 검색 증강 생성이다.",
                        "lecture.pdf",
                        1,
                        1,
                        1,
                        0.98
                ))
        ));

        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(systemPrompt.capture());
        verify(requestSpec).user(userPrompt.capture());

        assertThat(systemPrompt.getValue()).contains("반드시 JSON 객체 하나만 반환해라");
        assertThat(userPrompt.getValue()).contains("RAG가 뭐야?");
        assertThat(userPrompt.getValue()).contains("sectionId: sec-1");
        assertThat(generatedAnswer.answer()).contains("RAG");
        assertThat(generatedAnswer.citedSectionIds()).containsExactly("sec-1");
        assertThat(generatedAnswer.answerable()).isTrue();
    }

    @Test
    void throwsBusinessExceptionWhenGeneratedAnswerIsNotJson() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);

        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("이건 JSON이 아닙니다.");

        OllamaService ollamaService = new OllamaService(builder, objectMapper);

        assertThatThrownBy(() -> ollamaService.generateGroundedAnswer(new AnswerGenerationCommand(
                "RAG가 뭐야?",
                java.util.List.of(new RetrievedSection(
                        "sec-1",
                        "1. RAG Overview",
                        "RAG는 검색 증강 생성이다.",
                        "lecture.pdf",
                        1,
                        1,
                        1,
                        0.98
                ))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessage("AI 응답을 해석하는 데 실패했습니다.");
    }

    @Test
    void generatesQuizFromRetrievedSections() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);

        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("""
                {
                  "quizType": "MULTIPLE_CHOICE",
                  "questionText": "RAG의 의미로 가장 알맞은 것은?",
                  "choices": ["검색 증강 생성", "정렬 알고리즘"],
                  "answer": "검색 증강 생성",
                  "explanation": "RAG는 검색된 문맥을 활용해 답변 품질을 높이는 방식입니다.",
                  "sourceSectionIds": [100]
                }
                """);

        OllamaService ollamaService = new OllamaService(builder, objectMapper);

        GeneratedQuizDraft quiz = ollamaService.generateQuiz(new QuizGenerationPrompt(
                "RAG 단원",
                "MULTIPLE_CHOICE",
                "중",
                1,
                java.util.List.of(new RetrievedSection(
                        "100",
                        "RAG",
                        "RAG는 검색 증강 생성이다.",
                        "lecture.pdf",
                        1,
                        1,
                        1,
                        0.95
                )),
                ResolvedPromptTemplate.fallback()
        ));

        ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(userPrompt.capture());

        assertThat(userPrompt.getValue()).contains("RAG 단원");
        assertThat(userPrompt.getValue()).contains("sectionId: 100");
        assertThat(quiz.quizType()).isEqualTo("MULTIPLE_CHOICE");
        assertThat(quiz.questionText()).contains("RAG");
        assertThat(quiz.choices()).contains("검색 증강 생성");
        assertThat(quiz.answer()).isEqualTo("검색 증강 생성");
        assertThat(quiz.sourceSectionIds()).containsExactly(100L);
    }
}
