package org.example.snow.document.application.chunking;

import org.example.snow.document.domain.ExtractedDocument;
import org.example.snow.document.domain.Section;
import org.example.snow.document.domain.SourceUnit;
import org.example.snow.document.domain.SourceUnitType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class SectionBuilder {

    private static final Pattern NUMBERED_HEADING_PATTERN = Pattern.compile(
            "^(?:\\d+(?:[.-]\\d+)*[.)]?|[A-Z][.)]|[IVXLCM]+[.)]|\\([^)]+\\)|제\\s*\\d+\\s*[장절조])\\s+.+$"
    );
    private static final Pattern GENERIC_PAGE_HEADING_PATTERN = Pattern.compile("^Page\\s+\\d+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GENERIC_SLIDE_HEADING_PATTERN = Pattern.compile("^Slide\\s+\\d+$", Pattern.CASE_INSENSITIVE);

    public List<Section> build(ExtractedDocument document) {
        List<Section> sections = new ArrayList<>();
        SectionAccumulator current = null;
        int order = 1;

        for (SourceUnit sourceUnit : document.sourceUnits()) {
            SectionCandidate candidate = buildCandidate(document.sourceType(), sourceUnit);
            if (candidate.text().isBlank()) {
                continue;
            }

            if (current == null) {
                current = SectionAccumulator.start(candidate, document.sourceType());
                continue;
            }

            if (shouldStartNewSection(current, candidate)) {
                sections.add(current.toSection(order++));
                current = SectionAccumulator.start(candidate, document.sourceType());
                continue;
            }

            current.append(candidate);
        }

        if (current != null) {
            sections.add(current.toSection(order));
        }

        return sections;
    }

    private boolean shouldStartNewSection(SectionAccumulator current, SectionCandidate candidate) {
        if (!candidate.explicitHeading()) {
            return false;
        }

        return !current.matchesHeading(candidate.heading());
    }

    private SectionCandidate buildCandidate(SourceUnitType sourceType, SourceUnit sourceUnit) {
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
        String normalizedSourceHeading = normalizeHeading(sourceHeading);
        if (isMeaningfulSourceHeading(sourceType, normalizedSourceHeading)) {
            return new HeadingResolution(normalizedSourceHeading, false, true);
        }

        for (String line : lines) {
            if (isStructuralHeading(line)) {
                return new HeadingResolution(normalizeHeading(line), true, true);
            }
        }

        return new HeadingResolution(defaultSectionHeading(fallbackIndex), false, false);
    }

    private String stripHeadingFromText(String text, HeadingResolution headingResolution) {
        if (!headingResolution.fromText()) {
            return text.trim();
        }

        List<String> lines = text.lines().toList();
        String normalizedHeading = normalizeHeading(headingResolution.heading());
        boolean removed = false;
        StringBuilder builder = new StringBuilder();

        for (String line : lines) {
            String trimmedLine = line.trim();
            if (!removed && StringUtils.hasText(trimmedLine) && normalizeHeading(trimmedLine).equals(normalizedHeading)) {
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
            return !GENERIC_PAGE_HEADING_PATTERN.matcher(heading).matches();
        }
        if (sourceType == SourceUnitType.SLIDE) {
            return !GENERIC_SLIDE_HEADING_PATTERN.matcher(heading).matches();
        }
        return true;
    }

    private boolean isStructuralHeading(String line) {
        String normalizedLine = normalizeHeading(line);
        if (!StringUtils.hasText(normalizedLine)) {
            return false;
        }
        if (normalizedLine.startsWith("-") || normalizedLine.startsWith("*") || normalizedLine.startsWith("•")) {
            return false;
        }
        if (normalizedLine.length() > 80) {
            return false;
        }
        if (NUMBERED_HEADING_PATTERN.matcher(normalizedLine).matches()) {
            return true;
        }
        if (endsWithSentencePunctuation(normalizedLine)) {
            return false;
        }

        int wordCount = normalizedLine.split("\\s+").length;
        return normalizedLine.length() <= 40 && wordCount <= 10;
    }

    private boolean endsWithSentencePunctuation(String line) {
        String lowerCaseLine = line.toLowerCase(Locale.ROOT);
        return lowerCaseLine.endsWith(".")
                || lowerCaseLine.endsWith("?")
                || lowerCaseLine.endsWith("!")
                || lowerCaseLine.endsWith("다.")
                || lowerCaseLine.endsWith("니다.")
                || lowerCaseLine.endsWith(";");
    }

    private String defaultSectionHeading(int index) {
        return "Section " + index;
    }

    private String normalizeHeading(String heading) {
        return heading == null ? "" : heading.trim().replaceAll("\\s+", " ");
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

        boolean matchesHeading(String candidateHeading) {
            return heading.equalsIgnoreCase(candidateHeading);
        }

        Section toSection(int order) {
            return new Section(
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
