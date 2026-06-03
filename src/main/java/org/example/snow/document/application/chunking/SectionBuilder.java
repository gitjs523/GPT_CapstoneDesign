package org.example.snow.document.application.chunking;

import org.example.snow.document.domain.ExtractedDocument;
import org.example.snow.document.domain.ExtractedSection;
import org.example.snow.document.domain.ExtractedSourceUnit;
import org.example.snow.document.domain.SourceUnitType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * SourceUnit을 의미 단위 Section으로 묶는다.
 *
 * 경계 신호는 헤딩(OutlineMarkers로 번호/불릿/짧은 제목 인식)이며, 번호 접두사를 정규화해
 * 비교하므로 구분 슬라이드("1. 개요")와 본문("개요")이 한 Section으로 합쳐진다.
 * 한 Section이 너무 커지면(MAX_SECTION_CHARS) 페이지 경계에서 분할한다.
 */
@Component
public class SectionBuilder {

    private static final int MAX_SECTION_CHARS = 1_200;
    private static final int MIN_SECTION_CHARS = 30;

    public List<ExtractedSection> build(ExtractedDocument document) {
        List<ExtractedSection> sections = new ArrayList<>();
        SectionAccumulator current = null;
        int order = 1;

        for (ExtractedSourceUnit sourceUnit : document.sourceUnits()) {
            SectionCandidate candidate = buildCandidate(document.sourceType(), sourceUnit);
            if (candidate.text().isBlank()) {
                continue;
            }

            if (current == null) {
                current = SectionAccumulator.start(candidate, document.sourceType());
                continue;
            }

            boolean wouldExceed = current.length() + candidate.text().length() > MAX_SECTION_CHARS;
            if (shouldStartNewSection(current, candidate) || wouldExceed) {
                sections.add(current.toSection(order++));
                current = SectionAccumulator.start(candidate, document.sourceType());
                continue;
            }

            current.append(candidate);
        }

        if (current != null) {
            sections.add(current.toSection(order));
        }

        return mergeTinySections(sections);
    }

    /**
     * 본문이 너무 짧은 Section(표지/구분 슬라이드 등)을 인접 Section으로 흡수한다.
     * 다음 Section으로 흡수하되, 마지막이면 이전 Section으로, 단독이면 그대로 둔다.
     */
    private List<ExtractedSection> mergeTinySections(List<ExtractedSection> sections) {
        List<ExtractedSection> work = new ArrayList<>(sections);
        List<ExtractedSection> out = new ArrayList<>();

        for (int i = 0; i < work.size(); i++) {
            ExtractedSection section = work.get(i);
            if (section.text().length() >= MIN_SECTION_CHARS) {
                out.add(section);
                continue;
            }
            if (i + 1 < work.size()) {
                work.set(i + 1, merge(section, work.get(i + 1), work.get(i + 1).heading()));
            } else if (!out.isEmpty()) {
                int last = out.size() - 1;
                out.set(last, merge(out.get(last), section, out.get(last).heading()));
            } else {
                out.add(section); // 단독 Section은 보존
            }
        }

        List<ExtractedSection> renumbered = new ArrayList<>(out.size());
        int order = 1;
        for (ExtractedSection section : out) {
            renumbered.add(new ExtractedSection(
                    order++, section.heading(), section.text(), section.sourceType(),
                    section.sourceStartIndex(), section.sourceEndIndex(), section.sourceIndices()));
        }
        return renumbered;
    }

    private ExtractedSection merge(ExtractedSection first, ExtractedSection second, String heading) {
        List<Integer> indices = new ArrayList<>(first.sourceIndices());
        for (Integer idx : second.sourceIndices()) {
            if (!indices.contains(idx)) {
                indices.add(idx);
            }
        }
        String text = (first.text() + "\n\n" + second.text()).trim();
        return new ExtractedSection(
                first.order(),
                heading,
                text,
                first.sourceType(),
                Math.min(first.sourceStartIndex(), second.sourceStartIndex()),
                Math.max(first.sourceEndIndex(), second.sourceEndIndex()),
                indices
        );
    }

    private boolean shouldStartNewSection(SectionAccumulator current, SectionCandidate candidate) {
        if (!candidate.explicitHeading()) {
            return false;
        }
        return !current.matchesHeading(candidate.heading());
    }

    private SectionCandidate buildCandidate(SourceUnitType sourceType, ExtractedSourceUnit sourceUnit) {
        String normalizedText = sourceUnit.text() == null ? "" : sourceUnit.text().trim();
        if (normalizedText.isBlank()) {
            return new SectionCandidate(defaultSectionHeading(sourceUnit.index()), "", false, sourceUnit.index());
        }

        List<String> lines = normalizedText.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .limit(3)
                .toList();

        HeadingResolution headingResolution = resolveHeading(sourceType, sourceUnit.heading(), lines, sourceUnit.index());
        String bodyText = stripHeadingFromText(normalizedText, headingResolution);
        if (bodyText.isBlank()) {
            bodyText = normalizedText;
        }

        return new SectionCandidate(
                headingResolution.heading(),
                bodyText,
                headingResolution.explicitHeading(),
                sourceUnit.index()
        );
    }

    private HeadingResolution resolveHeading(
            SourceUnitType sourceType,
            String sourceHeading,
            List<String> lines,
            int fallbackIndex
    ) {
        String normalizedSourceHeading = OutlineMarkers.normalizeHeading(sourceHeading);
        if (isMeaningfulSourceHeading(sourceType, normalizedSourceHeading)) {
            return new HeadingResolution(normalizedSourceHeading, false, true);
        }

        for (String line : lines) {
            if (isHeadingCandidate(line)) {
                return new HeadingResolution(OutlineMarkers.normalizeHeading(line), true, true);
            }
        }

        return new HeadingResolution(defaultSectionHeading(fallbackIndex), false, false);
    }

    private String stripHeadingFromText(String text, HeadingResolution headingResolution) {
        if (!headingResolution.fromText()) {
            return text.trim();
        }

        List<String> lines = text.lines().toList();
        String normalizedHeading = OutlineMarkers.normalizeHeading(headingResolution.heading());
        boolean removed = false;
        StringBuilder builder = new StringBuilder();

        for (String line : lines) {
            String trimmedLine = line.trim();
            if (!removed && StringUtils.hasText(trimmedLine)
                    && OutlineMarkers.normalizeHeading(trimmedLine).equals(normalizedHeading)) {
                removed = true;
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line);
        }

        String strippedText = builder.toString().trim();
        return strippedText.isBlank() ? text.trim() : strippedText;
    }

    private boolean isMeaningfulSourceHeading(SourceUnitType sourceType, String heading) {
        if (!StringUtils.hasText(heading)) {
            return false;
        }
        if (sourceType == SourceUnitType.PAGE) {
            return !OutlineMarkers.isGenericPageHeading(heading);
        }
        if (sourceType == SourceUnitType.SLIDE) {
            return !OutlineMarkers.isGenericSlideHeading(heading);
        }
        return true;
    }

    /**
     * 줄이 Section 헤딩 후보인가.
     * 번호 헤딩이거나 짧은 제목줄만 인정한다. 마커로 시작해도 길면(이전 페이지에서 넘어온
     * 긴 불릿 문장 등) 헤딩으로 보지 않는다 — isShortTitleLine이 길이/문장 여부를 판정한다.
     */
    private boolean isHeadingCandidate(String line) {
        return OutlineMarkers.isNumberedHeading(line)
                || OutlineMarkers.isShortTitleLine(line);
    }

    private String defaultSectionHeading(int index) {
        return "Section " + index;
    }

    private record SectionCandidate(
            String heading,
            String text,
            boolean explicitHeading,
            int sourceIndex
    ) {
    }

    private record HeadingResolution(
            String heading,
            boolean fromText,
            boolean explicitHeading
    ) {
    }

    private static final class SectionAccumulator {

        private final String heading;
        private final SourceUnitType sourceType;
        private final List<Integer> sourceIndices = new ArrayList<>();
        private final StringBuilder textBuilder = new StringBuilder();

        private SectionAccumulator(String heading, SourceUnitType sourceType) {
            this.heading = heading;
            this.sourceType = sourceType;
        }

        static SectionAccumulator start(SectionCandidate candidate, SourceUnitType sourceType) {
            SectionAccumulator accumulator = new SectionAccumulator(candidate.heading(), sourceType);
            accumulator.append(candidate);
            return accumulator;
        }

        void append(SectionCandidate candidate) {
            if (!sourceIndices.contains(candidate.sourceIndex())) {
                sourceIndices.add(candidate.sourceIndex());
            }
            if (textBuilder.length() > 0) {
                textBuilder.append("\n\n");
            }
            textBuilder.append(candidate.text().trim());
        }

        int length() {
            return textBuilder.length();
        }

        /** 번호 접두사를 무시하고 헤딩을 비교한다("1. 개요" ≡ "개요"). */
        boolean matchesHeading(String candidateHeading) {
            return OutlineMarkers.stripLeadingMarker(heading)
                    .equalsIgnoreCase(OutlineMarkers.stripLeadingMarker(candidateHeading));
        }

        ExtractedSection toSection(int order) {
            return new ExtractedSection(
                    order,
                    heading,
                    textBuilder.toString().trim(),
                    sourceType,
                    sourceIndices.get(0),
                    sourceIndices.get(sourceIndices.size() - 1),
                    sourceIndices
            );
        }
    }
}
