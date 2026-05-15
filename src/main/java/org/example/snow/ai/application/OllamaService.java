package org.example.snow.ai.application;

import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private static final String QUIZ_ANSWER_SYSTEM_PROMPT = """
            너는 생성된 퀴즈를 해설하는 학습 튜터 AI다.
            반드시 제공된 퀴즈 정보와 근거 문맥만 바탕으로 답변해라.
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
            - 문제의 정답 이유, 오답 이유, 개념 설명은 퀴즈 정보와 근거 문맥 안에서만 설명한다.
            - answerable이 true면 citedSectionIds에는 실제로 근거로 사용한 sectionId만 넣는다.
            - 근거 섹션 없이 퀴즈 정보만으로 답변했다면 citedSectionIds는 빈 배열로 둔다.
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

    private static final String SUMMARY_SYSTEM_PROMPT = """
        너는 학습 지원 AI다.
        입력된 문서를 한국어로 요약해라.
        핵심 개념과 주요 내용을 중심으로 3~5문장으로 간결하게 작성해라.
        불필요한 마크다운 기호는 사용하지 마라.
        """;

    private static final String SECTION_SUMMARY_SYSTEM_PROMPT = """
        너는 학습 지원 AI다.
        사용자가 요청한 특정 주제 또는 단원과 관련된 내용만 한국어로 요약해라.
        입력 문서 전체를 다 요약하지 말고, 요청한 주제와 직접 관련된 부분만 3~5문장으로 간결하게 정리해라.
        관련 내용이 충분하지 않으면 제공된 내용 기준으로만 최대한 간단히 요약해라.
        불필요한 마크다운 기호는 사용하지 마라.
        """;

    public String generateSummary(String content) {
    if (!StringUtils.hasText(content)) {
        throw new IllegalArgumentException("문서 내용은 필수입니다.");
    }

    return chatClient.prompt()
            .system(SUMMARY_SYSTEM_PROMPT)
            .user(content.trim())
            .call()
            .content();
    }

    public String generateSectionSummary(String topic, String content) {
    if (!StringUtils.hasText(topic)) {
        throw new IllegalArgumentException("주제는 필수입니다.");
    }
    if (!StringUtils.hasText(content)) {
        throw new IllegalArgumentException("문서 내용은 필수입니다.");
    }

    String prompt = """
            요청 주제:
            %s

            문서 내용:
            %s

            위 문서 내용 중 요청 주제와 관련된 부분만 요약해라.
            """.formatted(topic.trim(), content.trim());

    return chatClient.prompt()
            .system(SECTION_SUMMARY_SYSTEM_PROMPT)
            .user(prompt)
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

    public GeneratedQuizDraft generateQuiz(QuizGenerationPrompt prompt) {
        try {
            String rawContent = chatClient.prompt()
                    .system(prompt.promptTemplate().systemPrompt())
                    .user(buildQuizPrompt(prompt))
                    .call()
                    .content();

            return parseGeneratedQuiz(rawContent, prompt.quizType());
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_GENERATION_FAILED, exception);
        }
    }

    public GeneratedAnswer generateQuizAnswer(QuizAnswerGenerationCommand command) {
        try {
            String rawContent = chatClient.prompt()
                    .system(QUIZ_ANSWER_SYSTEM_PROMPT)
                    .user(buildQuizAnswerPrompt(command))
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

    private String buildQuizAnswerPrompt(QuizAnswerGenerationCommand command) {
        String contextBlocks = command.sourceSections().stream()
                .map(RetrievedSection::toPromptBlock)
                .reduce((left, right) -> left + "\n\n---\n\n" + right)
                .orElse("근거 섹션 없음");

        GeneratedQuizResult quiz = GeneratedQuizResult.from(command.quiz());
        String choices = StringUtils.hasText(quiz.choices()) ? quiz.choices() : "선택지 없음";
        String explanation = StringUtils.hasText(quiz.explanation()) ? quiz.explanation() : "기존 해설 없음";

        return """
                사용자 질문:
                %s

                퀴즈 정보:
                quizId: %s
                quizType: %s
                questionText:
                %s

                choices:
                %s

                answer:
                %s

                explanation:
                %s

                근거 문맥:
                %s

                위 퀴즈 정보와 근거 문맥만 바탕으로 사용자 질문에 답변해라.
                답변이 가능하면 answerable=true, 불가능하면 answerable=false로 반환해라.
                """.formatted(
                command.question(),
                quiz.quizId(),
                quiz.quizType(),
                quiz.questionText(),
                choices,
                quiz.answer(),
                explanation,
                contextBlocks
        );
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

    private String buildQuizPrompt(QuizGenerationPrompt prompt) {
        String contextBlocks = prompt.sections().stream()
                .map(RetrievedSection::toPromptBlock)
                .reduce((left, right) -> left + "\n\n---\n\n" + right)
                .orElse("");

        return prompt.promptTemplate().userPromptTemplate()
                .replace("{scopeText}", prompt.scopeText())
                .replace("{quizType}", prompt.quizType())
                .replace("{difficulty}", prompt.difficulty())
                .replace("{quizOrder}", Integer.toString(prompt.quizOrder()))
                .replace("{contextSections}", contextBlocks)
                .replace("{outputSchema}", prompt.promptTemplate().outputSchema());
    }

    private GeneratedQuizDraft parseGeneratedQuiz(String rawContent, String fallbackQuizType) {
        if (!StringUtils.hasText(rawContent)) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_PARSING_FAILED);
        }

        String jsonContent = extractJsonPayload(rawContent);

        try {
            JsonNode payload = objectMapper.readTree(jsonContent);
            if (payload.isArray()) {
                if (payload.isEmpty()) {
                    throw new IllegalArgumentException("생성된 문제 배열이 비어 있습니다.");
                }
                payload = payload.get(0);
            }

            String quizType = readOptionalText(payload, "quizType", "quiz_type");
            String questionText = readRequiredText(payload, "questionText", "question_text", "question");
            String choices = readChoices(payload.path("choices"));
            String answer = readRequiredText(payload, "answer");
            String explanation = readOptionalText(payload, "explanation");
            List<Long> sourceSectionIds = readLongArray(payload, "sourceSectionIds", "source_section_ids");

            return new GeneratedQuizDraft(
                    StringUtils.hasText(quizType) ? quizType : fallbackQuizType,
                    questionText,
                    choices,
                    answer,
                    explanation,
                    sourceSectionIds
            );
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

    private String readRequiredText(JsonNode payload, String... fieldNames) {
        String value = readOptionalText(payload, fieldNames);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(String.join("/", fieldNames) + " 필드는 비어 있을 수 없습니다.");
        }
        return value;
    }

    private String readOptionalText(JsonNode payload, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode valueNode = payload.path(fieldName);
            if (!valueNode.isMissingNode() && !valueNode.isNull() && StringUtils.hasText(valueNode.asText())) {
                return valueNode.asText().trim();
            }
        }
        return "";
    }

    private String readChoices(JsonNode choicesNode) throws JsonProcessingException {
        if (choicesNode == null || choicesNode.isMissingNode() || choicesNode.isNull()) {
            return "";
        }
        if (choicesNode.isArray() || choicesNode.isObject()) {
            return objectMapper.writeValueAsString(choicesNode);
        }
        return choicesNode.asText("");
    }

    private List<Long> readLongArray(JsonNode payload, String... fieldNames) {
        JsonNode arrayNode = null;
        for (String fieldName : fieldNames) {
            JsonNode candidate = payload.path(fieldName);
            if (candidate.isArray()) {
                arrayNode = candidate;
                break;
            }
        }
        if (arrayNode == null) {
            return List.of();
        }

        return StreamSupport.stream(arrayNode.spliterator(), false)
                .map(JsonNode::asText)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(this::parseLongOrNull)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private Long parseLongOrNull(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String extractJsonPayload(String rawContent) {
        String trimmed = rawContent.trim()
                .replaceFirst("^```(?:json)?\\s*", "")
                .replaceFirst("\\s*```$", "");

        int objectStart = trimmed.indexOf('{');
        int objectEnd = trimmed.lastIndexOf('}');
        int arrayStart = trimmed.indexOf('[');
        int arrayEnd = trimmed.lastIndexOf(']');

        if (arrayStart >= 0 && arrayEnd > arrayStart && (objectStart < 0 || arrayStart < objectStart)) {
            return trimmed.substring(arrayStart, arrayEnd + 1);
        }
        if (objectStart >= 0 && objectEnd > objectStart) {
            return trimmed.substring(objectStart, objectEnd + 1);
        }
        return trimmed;
    }

    private Stream<String> streamArray(JsonNode arrayNode) {
        return StreamSupport.stream(arrayNode.spliterator(), false)
                .map(JsonNode::asText);
    }
}
