package org.example.snow.document.application.chunking;

import org.example.snow.document.domain.ExtractedChunk;
import org.example.snow.document.domain.ExtractedSection;
import org.example.snow.document.domain.SourceUnitType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * 임베딩 유사도로 "같은 개념을 공유하는 블록 집단"을 Section으로 묶는다 (TextTiling 계열).
 *
 * 경계: 인접 블록 cosine이 상대 깊이(mean - k*std)보다 낮은 국소 최소점. baseline cosine이
 * 문서마다 달라 고정 임계값은 취약하므로 상대 깊이를 쓴다. 이후 maxSectionChars 초과 Section은
 * 내부 최저 유사도 seam에서 재귀 분할(size cap, LLM context 토큰 예산 보호)한다.
 *
 * label: Section 내부 heading-like 줄을 티어 우선순위로 뽑되, 반복 제목·문서명·제네릭은 강등한다.
 * (마커는 경계가 아니라 label 힌트로만 쓴다 — OutlineMarkers.)
 *
 * 순수 컴포넌트: 임베딩 벡터를 입력으로 받아 인프라에 의존하지 않는다.
 */
@Component
public class SemanticSectionizer {

    private static final Pattern STRONG_HEADING = Pattern.compile(
            "^(?:\\d+\\s*(?:주차|장|절|단원|차시)|제\\s*\\d+\\s*[장절조항]|\\d+\\.)\\s+\\S.*");

    private static final Set<String> GENERIC_LABELS = Set.of(
            "학습정리", "목차", "차례", "요약", "정리", "개요",
            "introduction", "intro", "overview", "contents", "agenda");

    private final double k;
    private final int maxSectionChars;

    public SemanticSectionizer(
            @Value("${chunking.semantic.k:1.0}") double k,
            @Value("${chunking.semantic.max-section-chars:4000}") int maxSectionChars
    ) {
        this.k = k;
        this.maxSectionChars = maxSectionChars;
    }

    /** Section 1개 = ExtractedSection + 그에 속한 Chunk(블록)들과 각 임베딩. */
    public record ChunkVector(ExtractedChunk chunk, float[] embedding) {
    }

    public record SectionGroup(ExtractedSection section, List<ChunkVector> chunks) {
    }

    /**
     * @param blocks       문단 블록(순서 보존)
     * @param embeddings   blocks와 같은 순서·길이의 임베딩 벡터
     * @param docTitleHint label 억제용 문서 제목 힌트(없으면 빈 문자열)
     */
    public List<SectionGroup> sectionize(List<TextBlock> blocks, List<float[]> embeddings, String docTitleHint) {
        if (blocks.isEmpty()) {
            return List.of();
        }
        if (blocks.size() != embeddings.size()) {
            throw new IllegalArgumentException(
                    "blocks(" + blocks.size() + ")와 embeddings(" + embeddings.size() + ") 크기 불일치");
        }

        List<Integer> cuts = blocks.size() < 2
                ? List.of()
                : computeCuts(blocks, embeddings);

        String docTitleNorm = OutlineMarkers.normalizeHeading(docTitleHint);
        Set<String> runningTitles = detectRunningTitles(blocks);

        List<SectionGroup> groups = new ArrayList<>();
        List<Integer> cutList = new ArrayList<>(cuts);
        cutList.add(blocks.size());
        int start = 0;
        int sectionOrder = 1;
        int chunkOrder = 1;
        for (int cut : cutList) {
            String label = deriveLabel(blocks, start, cut, docTitleNorm, runningTitles);
            ExtractedSection section = buildSection(blocks, start, cut, sectionOrder++, label);
            List<ChunkVector> chunkVectors = new ArrayList<>();
            for (int i = start; i < cut; i++) {
                chunkVectors.add(new ChunkVector(buildChunk(blocks.get(i), chunkOrder++, label), embeddings.get(i)));
            }
            groups.add(new SectionGroup(section, chunkVectors));
            start = cut;
        }
        return groups;
    }

    private List<Integer> computeCuts(List<TextBlock> blocks, List<float[]> embeddings) {
        double[] sims = new double[blocks.size()];
        for (int i = 1; i < blocks.size(); i++) {
            sims[i] = cosine(embeddings.get(i - 1), embeddings.get(i));
        }
        double mean = mean(sims);
        double std = std(sims, mean);
        List<Integer> semantic = boundaries(sims, mean, std);
        return applySizeCap(blocks, sims, semantic);
    }

    /** 상대 깊이 경계: 국소 최소 & (mean - sim) > k*std. */
    private List<Integer> boundaries(double[] sims, double mean, double std) {
        List<Integer> b = new ArrayList<>();
        for (int i = 1; i < sims.length; i++) {
            boolean localMin = sims[i] <= sims[i - 1] && (i + 1 >= sims.length || sims[i] <= sims[i + 1]);
            if (localMin && (mean - sims[i]) > k * std) {
                b.add(i);
            }
        }
        return b;
    }

    /** maxSectionChars 초과 Section을 내부 최저 유사도 seam에서 재귀 분할. */
    private List<Integer> applySizeCap(List<TextBlock> blocks, double[] sims, List<Integer> semanticBounds) {
        TreeSet<Integer> all = new TreeSet<>(semanticBounds);
        Deque<int[]> stack = new ArrayDeque<>();
        int prev = 0;
        List<Integer> cuts = new ArrayList<>(all);
        cuts.add(blocks.size());
        for (int cut : cuts) {
            stack.push(new int[]{prev, cut});
            prev = cut;
        }
        while (!stack.isEmpty()) {
            int[] seg = stack.pop();
            int start = seg[0];
            int end = seg[1];
            if (end - start <= 1 || charLen(blocks, start, end) <= maxSectionChars) {
                continue;
            }
            int best = -1;
            double min = Double.MAX_VALUE;
            for (int i = start + 1; i < end; i++) {
                if (sims[i] < min) {
                    min = sims[i];
                    best = i;
                }
            }
            if (best < 0) {
                continue;
            }
            all.add(best);
            stack.push(new int[]{start, best});
            stack.push(new int[]{best, end});
        }
        return new ArrayList<>(all);
    }

    private ExtractedSection buildSection(List<TextBlock> blocks, int start, int end, int order, String label) {
        StringBuilder text = new StringBuilder();
        List<Integer> pages = new ArrayList<>();
        int minPage = Integer.MAX_VALUE;
        int maxPage = Integer.MIN_VALUE;
        for (int i = start; i < end; i++) {
            TextBlock block = blocks.get(i);
            if (text.length() > 0) {
                text.append("\n\n");
            }
            text.append(block.text());
            if (!pages.contains(block.page())) {
                pages.add(block.page());
            }
            minPage = Math.min(minPage, block.page());
            maxPage = Math.max(maxPage, block.page());
        }
        return new ExtractedSection(
                order, label, text.toString(), blocks.get(start).sourceType(), minPage, maxPage, pages);
    }

    private ExtractedChunk buildChunk(TextBlock block, int order, String label) {
        return new ExtractedChunk(
                order, label, block.text(), block.sourceType(),
                block.page(), block.page(), List.of(block.page()));
    }

    // ---- label ----

    private String deriveLabel(List<TextBlock> blocks, int start, int end, String docTitleNorm, Set<String> running) {
        String strong = null, numbered = null, shortTitle = null, firstLine = null;
        String strongSup = null, numberedSup = null, shortTitleSup = null;
        for (int i = start; i < end; i++) {
            for (String raw : blocks.get(i).text().split("\n")) {
                String line = raw.trim();
                if (line.isEmpty()) {
                    continue;
                }
                String stripped = OutlineMarkers.stripLeadingMarker(line);
                if (stripped.isBlank()) {
                    continue;
                }
                boolean suppressed = isSuppressed(stripped, docTitleNorm, running);
                if (STRONG_HEADING.matcher(line).matches()) {
                    if (suppressed) {
                        if (strongSup == null) strongSup = stripped;
                    } else if (strong == null) {
                        strong = stripped;
                    }
                } else if (OutlineMarkers.isNumberedHeading(line)) {
                    if (suppressed) {
                        if (numberedSup == null) numberedSup = stripped;
                    } else if (numbered == null) {
                        numbered = stripped;
                    }
                } else if (OutlineMarkers.isShortTitleLine(line)) {
                    if (suppressed) {
                        if (shortTitleSup == null) shortTitleSup = stripped;
                    } else if (shortTitle == null) {
                        shortTitle = stripped;
                    }
                }
                if (firstLine == null) {
                    firstLine = stripped;
                }
            }
        }
        String chosen = firstNonNull(strong, numbered, shortTitle, strongSup, numberedSup, shortTitleSup, firstLine);
        return chosen == null ? "" : chosen;
    }

    private boolean isSuppressed(String stripped, String docTitleNorm, Set<String> running) {
        String norm = OutlineMarkers.normalizeHeading(stripped);
        return norm.equalsIgnoreCase(docTitleNorm)
                || running.contains(norm)
                || GENERIC_LABELS.contains(norm.toLowerCase(Locale.ROOT));
    }

    private String firstNonNull(String... values) {
        for (String v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    /** 여러 블록에 반복 등장하는 heading-like 줄(=running title) 검출. */
    private Set<String> detectRunningTitles(List<TextBlock> blocks) {
        Map<String, Integer> count = new HashMap<>();
        for (TextBlock block : blocks) {
            Set<String> seen = new HashSet<>();
            for (String raw : block.text().split("\n")) {
                String line = raw.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (!STRONG_HEADING.matcher(line).matches()
                        && !OutlineMarkers.isNumberedHeading(line)
                        && !OutlineMarkers.isShortTitleLine(line)) {
                    continue;
                }
                String norm = OutlineMarkers.normalizeHeading(OutlineMarkers.stripLeadingMarker(line));
                if (!norm.isBlank()) {
                    seen.add(norm);
                }
            }
            for (String norm : seen) {
                count.merge(norm, 1, Integer::sum);
            }
        }
        int threshold = Math.max(3, (int) Math.ceil(blocks.size() * 0.10));
        Set<String> running = new LinkedHashSet<>();
        count.forEach((line, c) -> {
            if (c >= threshold) {
                running.add(line);
            }
        });
        return running;
    }

    // ---- 수치 유틸 ----

    private int charLen(List<TextBlock> blocks, int start, int end) {
        int sum = 0;
        for (int i = start; i < end; i++) {
            sum += blocks.get(i).text().length();
        }
        return sum;
    }

    private double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        return (na == 0 || nb == 0) ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private double mean(double[] sims) {
        if (sims.length < 2) {
            return 0;
        }
        double sum = 0;
        for (int i = 1; i < sims.length; i++) {
            sum += sims[i];
        }
        return sum / (sims.length - 1);
    }

    private double std(double[] sims, double mean) {
        if (sims.length < 2) {
            return 0;
        }
        double sum = 0;
        for (int i = 1; i < sims.length; i++) {
            sum += (sims[i] - mean) * (sims[i] - mean);
        }
        return Math.sqrt(sum / (sims.length - 1));
    }
}
