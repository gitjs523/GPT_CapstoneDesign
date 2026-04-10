package org.example.snow.ai.application;

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

        OllamaService ollamaService = new OllamaService(builder);

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

        OllamaService ollamaService = new OllamaService(builder);

        assertThatThrownBy(() -> ollamaService.ask("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("질문 q는 필수입니다.");
        verify(chatClient, never()).prompt();
    }
}
