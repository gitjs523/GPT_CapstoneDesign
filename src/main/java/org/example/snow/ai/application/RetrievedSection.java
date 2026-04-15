package org.example.snow.ai.application;

import org.springframework.util.StringUtils;

public record RetrievedSection(
        String sectionId,
        String heading,
        String text,
        String documentName,
        Integer sourceStartIndex,
        Integer sourceEndIndex,
        Integer rank,
        Double similarityScore
) {

    public RetrievedSection {
        if (!StringUtils.hasText(sectionId)) {
            throw new IllegalArgumentException("sectionId는 필수입니다.");
        }
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("검색된 문서 본문은 필수입니다.");
        }
        if (sourceStartIndex != null && sourceEndIndex != null && sourceStartIndex > sourceEndIndex) {
            throw new IllegalArgumentException("sourceStartIndex는 sourceEndIndex보다 클 수 없습니다.");
        }

        sectionId = sectionId.trim();
        heading = StringUtils.hasText(heading) ? heading.trim() : "제목 없음";
        text = text.trim();
        documentName = StringUtils.hasText(documentName) ? documentName.trim() : "문서명 미상";
    }

    public String toPromptBlock() {
        StringBuilder builder = new StringBuilder();
        builder.append("sectionId: ").append(sectionId).append('\n');
        builder.append("documentName: ").append(documentName).append('\n');
        builder.append("heading: ").append(heading).append('\n');

        if (sourceStartIndex != null || sourceEndIndex != null) {
            builder.append("location: ")
                    .append(sourceStartIndex == null ? "?" : sourceStartIndex)
                    .append("~")
                    .append(sourceEndIndex == null ? "?" : sourceEndIndex)
                    .append('\n');
        }
        if (rank != null) {
            builder.append("rank: ").append(rank).append('\n');
        }
        if (similarityScore != null) {
            builder.append("similarityScore: ").append(similarityScore).append('\n');
        }

        builder.append("text:\n").append(text);
        return builder.toString();
    }
}
