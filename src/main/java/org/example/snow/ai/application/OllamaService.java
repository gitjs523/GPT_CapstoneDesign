package org.example.snow.ai.application;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OllamaService {

    private static final String SYSTEM_PROMPT = """
            너는 학습 지원 AI다.
            사용자의 질문에 한국어로만 답변해라.
            답변은 3문장 이내로 간단하게 작성해라.
            불필요한 마크다운 기호는 사용하지 마라.
            """;

    private final ChatClient chatClient;

    public OllamaService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String ask(String question) {
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("질문 q는 필수입니다.");
        }

        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(question.trim())
                .call()
                .content();
    }
}
