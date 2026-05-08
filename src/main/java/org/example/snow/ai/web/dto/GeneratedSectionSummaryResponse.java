package org.example.snow.ai.web.dto;

public record GeneratedSectionSummaryResponse(
        String summary
) {
    public static GeneratedSectionSummaryResponse from(String summary) {
        return new GeneratedSectionSummaryResponse(summary);
    }
}