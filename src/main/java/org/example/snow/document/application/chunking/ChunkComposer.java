package org.example.snow.document.application.chunking;

import org.example.snow.document.domain.Chunk;
import org.example.snow.document.domain.ChunkStrategy;
import org.example.snow.document.domain.ExtractedDocument;
import org.example.snow.document.domain.Section;
import org.example.snow.document.domain.SourceUnit;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class ChunkComposer {

    private static final int TARGET_CHUNK_LENGTH = 1_100;
    private static final int MAX_CHUNK_LENGTH = 1_600;
    private static final Pattern SENTENCE_SPLIT_PATTERN = Pattern.compile("(?<=[.!?。])\\s+");

    public List<Chunk> compose(
            ExtractedDocument document,
            List<Section> sections,
            ChunkStrategy appliedStrategy
    ) {
        return switch (appliedStrategy) {
            case PAGE, SLIDE -> toSourceUnitChunks(document);
            case SECTION -> toSectionChunks(sections, false);
            case PARAGRAPH -> toSectionChunks(sections, true);
            case AUTO -> throw new IllegalStateException("AUTO strategy should have been resolved before chunking.");
        };
    }

    private List<Chunk> toSourceUnitChunks(ExtractedDocument document) {
        List<Chunk> chunks = new ArrayList<>();
        int order = 1;

        for (SourceUnit sourceUnit : document.sourceUnits()) {
            if (sourceUnit.text().isBlank()) {
                continue;
            }
            chunks.add(new Chunk(
                    order++,
                    sourceUnit.heading(),
                    sourceUnit.text(),
                    document.sourceType(),
                    sourceUnit.index(),
                    sourceUnit.index(),
                    List.of(sourceUnit.index())
            ));
        }

        return chunks;
    }

    private List<Chunk> toSectionChunks(List<Section> sections, boolean forceParagraphSplit) {
        List<Chunk> chunks = new ArrayList<>();
        int order = 1;

        for (Section section : sections) {
            List<String> chunkTexts = splitSectionText(section.text(), forceParagraphSplit);
            int part = 1;

            for (String chunkText : chunkTexts) {
                String heading = chunkTexts.size() == 1
                        ? section.heading()
                        : section.heading() + " (Part " + part++ + ")";
                chunks.add(new Chunk(
                        order++,
                        heading,
                        chunkText,
                        section.sourceType(),
                        section.sourceStartIndex(),
                        section.sourceEndIndex(),
                        section.sourceIndices()
                ));
            }
        }

        return chunks;
    }

    private List<String> splitSectionText(String text, boolean forceParagraphSplit) {
        String normalizedText = text.trim();
        if (normalizedText.isBlank()) {
            return List.of();
        }

        if (!forceParagraphSplit && normalizedText.length() <= MAX_CHUNK_LENGTH) {
            return List.of(normalizedText);
        }

        List<String> segments = splitIntoSegments(normalizedText);
        int limit = forceParagraphSplit ? TARGET_CHUNK_LENGTH : MAX_CHUNK_LENGTH;

        List<String> chunkTexts = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String segment : segments) {
            if (current.isEmpty()) {
                current.append(segment);
                continue;
            }

            if (current.length() + 2 + segment.length() > limit) {
                chunkTexts.add(current.toString().trim());
                current = new StringBuilder(segment);
                continue;
            }

            current.append("\n\n").append(segment);
        }

        if (!current.isEmpty()) {
            chunkTexts.add(current.toString().trim());
        }

        return chunkTexts;
    }

    private List<String> splitIntoSegments(String text) {
        List<String> segments = new ArrayList<>();
        String[] paragraphs = text.split("\n{2,}");

        for (String paragraph : paragraphs) {
            String normalizedParagraph = paragraph.trim();
            if (normalizedParagraph.isBlank()) {
                continue;
            }
            addSegment(segments, normalizedParagraph);
        }

        if (segments.isEmpty()) {
            addSegment(segments, text);
        }

        return segments;
    }

    private void addSegment(List<String> segments, String text) {
        String normalizedText = text.trim();
        if (normalizedText.isBlank()) {
            return;
        }

        if (normalizedText.length() <= MAX_CHUNK_LENGTH) {
            segments.add(normalizedText);
            return;
        }

        List<String> sentences = splitIntoSentences(normalizedText);
        if (sentences.size() <= 1) {
            segments.addAll(splitByLength(normalizedText, MAX_CHUNK_LENGTH));
            return;
        }

        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            if (current.isEmpty()) {
                current.append(sentence);
                continue;
            }

            if (current.length() + 1 + sentence.length() > MAX_CHUNK_LENGTH) {
                segments.add(current.toString().trim());
                current = new StringBuilder(sentence);
                continue;
            }

            current.append(' ').append(sentence);
        }

        if (!current.isEmpty()) {
            segments.add(current.toString().trim());
        }
    }

    private List<String> splitIntoSentences(String text) {
        return Arrays.stream(SENTENCE_SPLIT_PATTERN.split(text))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<String> splitByLength(String text, int maxLength) {
        List<String> segments = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + maxLength, text.length());
            segments.add(text.substring(start, end).trim());
            start = end;
        }

        return segments;
    }
}
