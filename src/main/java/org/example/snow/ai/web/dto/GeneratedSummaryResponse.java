package org.example.snow.ai.web.dto;

public record GeneratedSummaryResponse(
        String summary
) {
    public static GeneratedSummaryResponse from(String summary) {
        return new GeneratedSummaryResponse(summary);
    }
}