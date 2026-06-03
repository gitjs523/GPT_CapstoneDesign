package org.example.snow.document.application.chunking;

import org.example.snow.document.domain.ExtractedChunk;
import org.example.snow.document.domain.ExtractedSection;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Section 본문을 작은 검색 단위(Chunk)로 분리한다.
 *
 * Parent-Child Retrieval: Chunk는 검색(임베딩) 단위로 작게 유지하고, LLM context로는 부모
 * Section 전체를 사용한다. 목표 크기 ~400자, 인접 Chunk 간 ~80자 오버랩으로 경계 누락을 줄인다.
 * 문단/문장/길이 순으로 세그먼트를 만든 뒤 목표 크기까지 묶는다.
 */
@Component
public class ChunkComposer {

    private static final int TARGET_CHUNK_CHARS = 400;
    private static final int MAX_SEGMENT_CHARS = 400;
    private static final int OVERLAP_CHARS = 80;
    private static final Pattern SENTENCE_SPLIT_PATTERN = Pattern.compile("(?<=[.!?。])\\s+");

    public List<ExtractedChunk> compose(List<ExtractedSection> sections) {
        List<ExtractedChunk> chunks = new ArrayList<>();
        int order = 1;

        for (ExtractedSection section : sections) {
            List<String> parts = splitIntoChunks(section.text());
            int part = 1;
            for (String text : parts) {
                String heading = parts.size() == 1
                        ? section.heading()
                        : section.heading() + " (Part " + part++ + ")";
                chunks.add(new ExtractedChunk(
                        order++,
                        heading,
                        text,
                        section.sourceType(),
                        section.sourceStartIndex(),
                        section.sourceEndIndex(),
                        section.sourceIndices()
                ));
            }
        }
        return chunks;
    }

    private List<String> splitIntoChunks(String text) {
        List<String> segments = splitIntoSegments(text);
        if (segments.isEmpty()) {
            return List.of();
        }
        if (totalLength(segments) <= TARGET_CHUNK_CHARS) {
            return List.of(String.join(" ", segments).trim());
        }

        List<String> chunks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int currentLength = 0;

        for (String segment : segments) {
            if (currentLength > 0 && currentLength + segment.length() > TARGET_CHUNK_CHARS) {
                chunks.add(String.join(" ", current).trim());
                current = overlapTail(current);
                currentLength = totalLength(current);
            }
            current.add(segment);
            currentLength += segment.length() + 1;
        }
        if (!current.isEmpty()) {
            chunks.add(String.join(" ", current).trim());
        }
        return chunks;
    }

    /** 다음 Chunk 앞에 둘 오버랩 — 직전 Chunk의 끝 세그먼트들을 OVERLAP_CHARS 이내로 가져온다. */
    private List<String> overlapTail(List<String> segments) {
        List<String> tail = new ArrayList<>();
        int length = 0;
        for (int i = segments.size() - 1; i >= 0; i--) {
            int segmentLength = segments.get(i).length();
            if (length + segmentLength > OVERLAP_CHARS) {
                break; // 마지막 세그먼트가 오버랩보다 크면 오버랩 없음
            }
            tail.add(0, segments.get(i));
            length += segmentLength + 1;
        }
        return tail;
    }

    private int totalLength(List<String> segments) {
        int sum = 0;
        for (String segment : segments) {
            sum += segment.length() + 1;
        }
        return sum;
    }

    private List<String> splitIntoSegments(String text) {
        List<String> segments = new ArrayList<>();
        for (String paragraph : text.split("\n{2,}")) {
            String normalized = paragraph.trim();
            if (normalized.isBlank()) {
                continue;
            }
            addSegment(segments, normalized);
        }
        if (segments.isEmpty()) {
            addSegment(segments, text);
        }
        return segments;
    }

    private void addSegment(List<String> segments, String text) {
        String normalized = text.trim();
        if (normalized.isBlank()) {
            return;
        }
        if (normalized.length() <= MAX_SEGMENT_CHARS) {
            segments.add(normalized);
            return;
        }

        List<String> sentences = splitIntoSentences(normalized);
        if (sentences.size() <= 1) {
            segments.addAll(splitByLength(normalized, MAX_SEGMENT_CHARS));
            return;
        }

        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            if (current.isEmpty()) {
                current.append(sentence);
            } else if (current.length() + 1 + sentence.length() > MAX_SEGMENT_CHARS) {
                segments.add(current.toString().trim());
                current = new StringBuilder(sentence);
            } else {
                current.append(' ').append(sentence);
            }
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
