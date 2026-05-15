package org.example.snow.ai.application;

import org.example.snow.ai.domain.PromptTemplate;
import org.springframework.util.StringUtils;

public record ResolvedPromptTemplate(
        Long promptTemplateId,
        String systemPrompt,
        String userPromptTemplate,
        String outputSchema
) {

    static ResolvedPromptTemplate from(PromptTemplate promptTemplate) {
        if (promptTemplate == null) {
            return fallback();
        }

        ResolvedPromptTemplate fallback = fallback();
        return new ResolvedPromptTemplate(
                promptTemplate.getPromptTemplateId(),
                StringUtils.hasText(promptTemplate.getSystemPrompt())
                        ? promptTemplate.getSystemPrompt()
                        : fallback.systemPrompt(),
                StringUtils.hasText(promptTemplate.getUserPromptTemplate())
                        ? promptTemplate.getUserPromptTemplate()
                        : fallback.userPromptTemplate(),
                StringUtils.hasText(promptTemplate.getOutputSchema())
                        ? promptTemplate.getOutputSchema()
                        : fallback.outputSchema()
        );
    }

    static ResolvedPromptTemplate fallback() {
        return new ResolvedPromptTemplate(
                null,
                """
                        너는 강의자료 기반 학습 퀴즈 생성 AI다.
                        반드시 제공된 검색 문맥만 근거로 문제를 생성해라.
                        답변은 한국어로 작성해라.
                        반드시 JSON 객체 하나만 반환해라.
                        마크다운, 코드 블록, 설명 문장, 추가 텍스트는 금지한다.
                        """,
                """
                        생성 요청:
                        - 범위: {scopeText}
                        - 문제 유형: {quizType}
                        - 난이도: {difficulty}
                        - 현재 문제 번호: {quizOrder}

                        검색된 강의자료:
                        {contextSections}

                        출력 스키마:
                        {outputSchema}

                        규칙:
                        - 위 강의자료만 근거로 문제 1개를 생성한다.
                        - 객관식이면 choices는 JSON 배열 문자열로 작성한다.
                        - 주관식이면 choices는 빈 문자열로 둔다.
                        - sourceSectionIds에는 실제 근거로 사용한 sectionId만 숫자 배열로 넣는다.
                        """,
                """
                        {
                          "quizType": "string",
                          "questionText": "string",
                          "choices": ["string"],
                          "answer": "string",
                          "explanation": "string",
                          "sourceSectionIds": [1]
                        }
                        """
        );
    }
}
