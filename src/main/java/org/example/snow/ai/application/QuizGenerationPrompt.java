package org.example.snow.ai.application;

import java.util.List;

public record QuizGenerationPrompt(
        String scopeText,
        String quizType,
        String difficulty,
        int quizOrder,
        List<RetrievedSection> sections,
        ResolvedPromptTemplate promptTemplate
) {

    public QuizGenerationPrompt {
        sections = sections == null ? List.of() : List.copyOf(sections);
        promptTemplate = promptTemplate == null ? ResolvedPromptTemplate.fallback() : promptTemplate;
    }
}
