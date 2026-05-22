package org.example.snow.document.web.dto;

import org.example.snow.document.domain.AnalysisStatus;
import org.example.snow.document.domain.Document;

public record DocumentAnalysisStatusResponse(
        AnalysisStatus analysisStatus,
        String summaryText,
        String analysisErrorMessage
) {
    public static DocumentAnalysisStatusResponse from(Document document) {
        String summary = document.getAnalysisStatus() == AnalysisStatus.COMPLETED
                ? document.getSummaryText()
                : null;
        String errorMessage = document.getAnalysisStatus() == AnalysisStatus.FAILED
                ? document.getAnalysisErrorMessage()
                : null;
        return new DocumentAnalysisStatusResponse(document.getAnalysisStatus(), summary, errorMessage);
    }
}
