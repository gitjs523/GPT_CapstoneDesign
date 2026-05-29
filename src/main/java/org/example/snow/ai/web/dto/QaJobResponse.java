package org.example.snow.ai.web.dto;

import org.example.snow.ai.application.NotebookQaResult;
import org.example.snow.ai.application.QaJob;
import org.example.snow.ai.application.QaJobStatus;

import java.util.List;

public record QaJobResponse(
        String qaJobId,
        QaJobStatus status,
        String answer,
        Boolean answerable,
        List<Long> citedSectionIds
) {
    public static QaJobResponse from(QaJob job) {
        NotebookQaResult result = job.getResult();
        if (result == null) {
            return new QaJobResponse(job.getQaJobId(), job.getStatus(), null, null, null);
        }
        return new QaJobResponse(
                job.getQaJobId(),
                job.getStatus(),
                result.answer(),
                result.answerable(),
                result.citedSectionIds()
        );
    }
}
