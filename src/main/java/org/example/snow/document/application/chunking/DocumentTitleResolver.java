package org.example.snow.document.application.chunking;

import org.example.snow.document.domain.ExtractedSection;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 문서 제목(docTitle) 결정.
 *
 * 우선순위:
 *   1. 반복 라인(머리말) — 핸드아웃의 "경제학개론" 같은 running title
 *   2. 첫 섹션 헤딩 — 슬라이드 표지 등 (단, "Section N" 류 기본 헤딩은 제외)
 *   3. 정리된 파일명 (최후 폴백)
 *
 * 임베딩 contextual header의 [문서: ...] 부분과 UI 표시에 쓰인다.
 */
@Component
public class DocumentTitleResolver {

    public String resolve(List<String> repeatedLines, List<ExtractedSection> sections, String filename) {
        String fromRepeated = repeatedLines == null ? null : repeatedLines.stream()
                .map(OutlineMarkers::stripLeadingMarker)
                .filter(line -> !line.isBlank())
                .max(Comparator.comparingInt(String::length))
                .orElse(null);
        if (fromRepeated != null) {
            return fromRepeated;
        }

        if (sections != null && !sections.isEmpty()) {
            String heading = OutlineMarkers.stripLeadingMarker(sections.get(0).heading());
            if (!heading.isBlank() && !isGenericHeading(heading)) {
                return heading;
            }
        }

        return cleanFilename(filename);
    }

    private boolean isGenericHeading(String heading) {
        return heading.matches("(?i)Section\\s+\\d+")
                || OutlineMarkers.isGenericPageHeading(heading)
                || OutlineMarkers.isGenericSlideHeading(heading);
    }

    private String cleanFilename(String filename) {
        if (filename == null) {
            return "";
        }
        // 이중 확장자(예: "x.pptx.pdf")까지 제거
        String name = filename.replaceAll("(?i)(\\.(pdf|pptx?|docx?))+$", "");
        return name.replace('_', ' ').replaceAll("\\s+", " ").trim();
    }
}
