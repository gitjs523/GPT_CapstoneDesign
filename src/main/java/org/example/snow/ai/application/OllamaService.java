package org.example.snow.ai.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Service
public class OllamaService {

    private static final String SIMPLE_CHAT_SYSTEM_PROMPT = """
            너는 학습 지원 AI다.
            사용자의 질문에 한국어로만 답변해라.
            답변은 3문장 이내로 간단하게 작성해라.
            불필요한 마크다운 기호는 사용하지 마라.
            """;
    private static final String GROUNDED_ANSWER_SYSTEM_PROMPT = """
            너는 강의자료 기반 질의응답 AI다.
            반드시 제공된 검색 문맥만 근거로 답변해라.
            문맥으로 확인할 수 없는 내용은 추측하지 말고 "제공된 문맥만으로는 알 수 없습니다."라고 답해라.
            답변은 한국어로 작성해라.
            반드시 JSON 객체 하나만 반환해라.
            JSON 스키마는 아래와 같다.
            {
              "answer": "string",
              "citedSectionIds": ["string"],
              "answerable": true
            }
            규칙:
            - answer는 1~4문장으로 작성한다.
            - answerable이 true면 citedSectionIds에는 실제로 근거로 사용한 sectionId만 넣는다.
            - answerable이 false면 citedSectionIds는 빈 배열로 반환한다.
            - 마크다운, 코드 블록, 설명 문장, 추가 텍스트는 금지한다.
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public OllamaService(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public String ask(String question) {
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("질문 q는 필수입니다.");
        }

        return chatClient.prompt()
                .system(SIMPLE_CHAT_SYSTEM_PROMPT)
                .user(question.trim())
                .call()
                .content();
    }

    public GeneratedAnswer generateGroundedAnswer(AnswerGenerationCommand command) {
        try {
            String rawContent = chatClient.prompt()
                    .system(GROUNDED_ANSWER_SYSTEM_PROMPT)
                    .user(buildGroundedAnswerPrompt(command))
                    .call()
                    .content();

            return parseGeneratedAnswer(rawContent);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_GENERATION_FAILED, exception);
        }
    }

    private String buildGroundedAnswerPrompt(AnswerGenerationCommand command) {
        String contextBlocks = command.sections().stream()
                .map(RetrievedSection::toPromptBlock)
                .reduce((left, right) -> left + "\n\n---\n\n" + right)
                .orElse("");

        return """
                질문:
                %s

                검색된 문맥:
                %s

                위 문맥만 근거로 답변해라.
                답변이 가능하면 answerable=true, 불가능하면 answerable=false로 반환해라.
                """.formatted(command.question(), contextBlocks);
    }

    private GeneratedAnswer parseGeneratedAnswer(String rawContent) {
        if (!StringUtils.hasText(rawContent)) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_PARSING_FAILED);
        }

        String jsonContent = extractJson(rawContent);

        try {
            JsonNode payload = objectMapper.readTree(jsonContent);
            String answer = readRequiredText(payload, "answer");
            boolean answerable = payload.path("answerable").asBoolean(false);
            List<String> citedSectionIds = !payload.has("citedSectionIds") || !payload.get("citedSectionIds").isArray()
                    ? List.of()
                    : streamArray(payload.get("citedSectionIds"))
                            .filter(StringUtils::hasText)
                            .map(String::trim)
                            .distinct()
                            .toList();

            if (!answerable) {
                citedSectionIds = List.of();
            }

            return new GeneratedAnswer(answer, citedSectionIds, answerable);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_PARSING_FAILED, exception);
        }
    }

    private String extractJson(String rawContent) {
        String trimmed = rawContent.trim()
                .replaceFirst("^```(?:json)?\\s*", "")
                .replaceFirst("\\s*```$", "");

        int jsonStart = trimmed.indexOf('{');
        int jsonEnd = trimmed.lastIndexOf('}');

        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return trimmed.substring(jsonStart, jsonEnd + 1);
        }

        return trimmed;
    }

    private String readRequiredText(JsonNode payload, String fieldName) {
        String value = payload.path(fieldName).asText();
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " 필드는 비어 있을 수 없습니다.");
        }
        return value.trim();
    }

    private Stream<String> streamArray(JsonNode arrayNode) {
        return StreamSupport.stream(arrayNode.spliterator(), false)
                .map(JsonNode::asText);
    }
}
