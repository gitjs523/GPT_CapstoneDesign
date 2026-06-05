package org.example.snow.document.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.snow.document.application.chunking.BoilerplateFilter;
import org.example.snow.document.application.chunking.DocumentTitleResolver;
import org.example.snow.document.application.chunking.OutlineMarkers;
import org.example.snow.document.domain.ExtractedDocument;
import org.example.snow.document.domain.ExtractedSourceUnit;
import org.example.snow.document.infra.extractor.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 임베딩 기반(semantic) Section 분할 프로토타입.
 *
 * 목적: 마커 기반(현행 SectionBuilder)이 핸드아웃은 과대분할·슬라이드는 과소분할하는 문제를
 * 대신해, "같은 개념을 공유하는 text 집단"을 임베딩 유사도로 직접 묶을 수 있는지 검증한다.
 * 본 파이프라인은 건드리지 않는 일회성 진단 테스트다.
 *
 * 알고리즘 (TextTiling 계열):
 *   페이지 추출 → 전처리/boilerplate 제거 → 문단 블록 분할 → 각 블록 임베딩
 *   → 인접 블록 cosine 유사도 → 상대 깊이(국소 최소 & mean - sim > k*std) 지점을 경계로.
 *   고정 절대 임계값은 baseline cosine이 문서마다 달라(핸드아웃~0.66 vs 슬라이드~0.72) 취약하므로 쓰지 않는다.
 *
 * label: 섹션 내부에서 heading-like 줄을 티어 우선순위로 탐색(주차/장/절·top-level "N." > 기타 번호 > 짧은 제목줄).
 * 임베딩은 build/tmp/embed-cache 에 캐싱하므로 k/label 튜닝 재실행 시 재임베딩하지 않는다.
 *
 * 실행:
 *   GRADLE_USER_HOME=/tmp/gradle-home ./gradlew test --tests '*SemanticSectionPrototypeTest' \
 *     -Dmeasure.pdf=build/tmp/test/경제학개론_전반기.pdf --rerun-tasks
 *   옵션: -Dsem.k=1.2  -Dollama.embed.url=...  -Dollama.embed.model=...  -Dembed.nocache=true
 */
@EnabledIfSystemProperty(named = "measure.pdf", matches = ".+")
class SemanticSectionPrototypeTest {

    private static final int MIN_BLOCK_CHARS = 40;
    private static final int MAX_BLOCK_CHARS = 600;
    private static final int EMBED_BATCH = 16;
    private static final double[] K_SWEEP = {0.8, 1.0, 1.2, 1.5};
    private static final int LABEL_MAX = 46;

    /** 강한 헤딩: "N주차 …", "제N장 …", top-level "N. …" (하위 "N)" "①" 제외). */
    private static final Pattern STRONG_HEADING = Pattern.compile(
            "^(?:\\d+\\s*(?:주차|장|절|단원|차시)|제\\s*\\d+\\s*[장절조항]|\\d+\\.)\\s+\\S.*");

    /** 변별력 없는 제네릭 섹션명 — label 후보에서 강등. */
    private static final java.util.Set<String> GENERIC_LABELS = java.util.Set.of(
            "학습정리", "목차", "차례", "요약", "정리", "개요", "introduction", "intro", "overview", "contents", "agenda");

    // prototype()에서 채워 deriveLabel이 참조 (반복 제목/문서 제목 억제용)
    private String docTitleNorm = "";
    private java.util.Set<String> runningTitles = java.util.Set.of();

    private final PdfTextExtractor extractor = new PdfTextExtractor();
    private final TextPreprocessor preprocessor = new TextPreprocessor();
    private final BoilerplateFilter boilerplateFilter = new BoilerplateFilter();
    private final DocumentTitleResolver titleResolver = new DocumentTitleResolver();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private record Block(int page, String text) {
    }

    @Test
    void prototype() throws Exception {
        Path pdfPath = Path.of(System.getProperty("measure.pdf"));
        double k = Double.parseDouble(System.getProperty("sem.k", "1.0"));

        ExtractedDocument extracted = extractor.extract(
                new UploadedDocument(pdfPath.getFileName().toString(), "application/pdf",
                        Files.readAllBytes(pdfPath)));
        List<ExtractedSourceUnit> normalized = extracted.sourceUnits().stream()
                .map(u -> new ExtractedSourceUnit(u.index(), u.heading(), preprocessor.normalize(u.text())))
                .toList();
        BoilerplateFilter.Result filtered = boilerplateFilter.filter(normalized);
        ExtractedDocument cleaned = extracted.withSourceUnits(filtered.units());

        String docTitle = titleResolver.resolve(filtered.repeatedLines(), List.of(), cleaned.originalFilename());
        List<Block> blocks = toBlocks(cleaned.sourceUnits());
        this.docTitleNorm = OutlineMarkers.normalizeHeading(docTitle);
        this.runningTitles = detectRunningTitles(blocks);

        System.out.println("\n############ SEMANTIC SECTION PROTOTYPE ############");
        System.out.println("file        : " + pdfPath.getFileName());
        System.out.println("docTitle    : " + docTitle);
        System.out.println("pages       : " + cleaned.sourceUnits().size());
        System.out.println("blocks      : " + blocks.size());
        if (blocks.size() < 2) {
            System.out.println("블록이 2개 미만이라 분할 비교 불가.");
            return;
        }

        // 임베딩 (캐시 우선)
        List<float[]> vectors = embedWithCache(pdfPath, blocks);

        double[] sims = new double[blocks.size()];
        for (int i = 1; i < blocks.size(); i++) {
            sims[i] = cosine(vectors.get(i - 1), vectors.get(i));
        }
        printSimStats(sims);

        double mean = mean(sims);
        double std = std(sims, mean);

        // k 스윕: 재임베딩 없이 경계 수만 비교
        System.out.println("\n--- k 스윕 (상대 깊이) ---");
        for (double kv : K_SWEEP) {
            System.out.printf("  k=%.1f  cut<%.3f  → %d sections%n",
                    kv, mean - kv * std, boundaries(sims, mean, std, kv).size() + 1);
        }

        // 선택 k 상세 출력 + size cap(hybrid)
        int maxChars = Integer.parseInt(System.getProperty("sem.maxchars", "4000"));
        List<Integer> bounds = boundaries(sims, mean, std, k);
        List<Integer> capped = applySizeCap(blocks, sims, bounds, maxChars);
        int added = capped.size() - bounds.size();
        printSections(String.format("METHOD B+cap — k=%.1f maxchars=%d (semantic %d + cap split %d = %d sections)",
                k, maxChars, bounds.size() + 1, added, capped.size() + 1), blocks, sims, capped);

        System.out.println("\n--- 인접 유사도 시리즈 (S=semantic경계, C=cap분할) ---");
        for (int i = 1; i < blocks.size(); i++) {
            String mark = bounds.contains(i) ? " <S" : capped.contains(i) ? " <C" : "   ";
            System.out.printf("  [%3d] p%-3d sim=%.3f%s | %s%n",
                    i, blocks.get(i).page(), sims[i], mark, preview(blocks.get(i).text(), 56));
        }
        System.out.println("###################################################\n");
    }

    /** 상대 깊이 경계: 국소 최소 & (mean - sim) > k*std. */
    private List<Integer> boundaries(double[] sims, double mean, double std, double k) {
        List<Integer> b = new ArrayList<>();
        for (int i = 1; i < sims.length; i++) {
            boolean localMin = sims[i] <= sims[i - 1] && (i + 1 >= sims.length || sims[i] <= sims[i + 1]);
            if (localMin && (mean - sims[i]) > k * std) {
                b.add(i);
            }
        }
        return b;
    }

    /**
     * size cap(hybrid): semantic 경계로 자른 뒤 maxChars 초과 section을 내부 최저 유사도 seam에서
     * 재귀 분할한다(qwen3:4b context window 압박 완화). 블록은 ≤600자라 초과 section은 항상 ≥2블록 → 분할 가능.
     */
    private List<Integer> applySizeCap(List<Block> blocks, double[] sims, List<Integer> semanticBounds, int maxChars) {
        java.util.TreeSet<Integer> all = new java.util.TreeSet<>(semanticBounds);
        java.util.Deque<int[]> stack = new java.util.ArrayDeque<>();
        int prev = 0;
        List<Integer> cuts = new ArrayList<>(all);
        cuts.add(blocks.size());
        for (int cut : cuts) {
            stack.push(new int[]{prev, cut});
            prev = cut;
        }
        while (!stack.isEmpty()) {
            int[] seg = stack.pop();
            int start = seg[0], end = seg[1];
            if (end - start <= 1 || charLen(blocks, start, end) <= maxChars) {
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

    private int charLen(List<Block> blocks, int start, int endExclusive) {
        int sum = 0;
        for (int i = start; i < endExclusive; i++) {
            sum += blocks.get(i).text().length();
        }
        return sum;
    }

    // ---- label: 섹션 내 heading-like 줄 탐색 ----

    private String deriveLabel(List<Block> blocks, int start, int endExclusive) {
        // 변별력 있는 후보 우선: STRONG > 번호헤딩 > 짧은제목. 반복 제목/문서명/제네릭은 강등.
        String strong = null, numbered = null, shortTitle = null, firstLine = null;
        String strongSup = null, numberedSup = null, shortTitleSup = null; // 강등(억제) 후보
        for (int i = start; i < endExclusive; i++) {
            for (String raw : blocks.get(i).text().split("\n")) {
                String line = raw.trim();
                if (line.isEmpty()) {
                    continue;
                }
                String stripped = OutlineMarkers.stripLeadingMarker(line);
                if (stripped.isBlank()) {
                    continue;
                }
                boolean suppressed = isSuppressed(stripped);
                if (STRONG_HEADING.matcher(line).matches()) {
                    if (suppressed) { if (strongSup == null) strongSup = stripped; }
                    else if (strong == null) strong = stripped;
                } else if (OutlineMarkers.isNumberedHeading(line)) {
                    if (suppressed) { if (numberedSup == null) numberedSup = stripped; }
                    else if (numbered == null) numbered = stripped;
                } else if (OutlineMarkers.isShortTitleLine(line)) {
                    if (suppressed) { if (shortTitleSup == null) shortTitleSup = stripped; }
                    else if (shortTitle == null) shortTitle = stripped;
                }
                if (firstLine == null) {
                    firstLine = stripped;
                }
            }
        }
        String chosen = firstNonNull(strong, numbered, shortTitle,        // 변별력 후보
                strongSup, numberedSup, shortTitleSup, firstLine);        // 폴백(억제 후보 포함)
        return clip(chosen == null ? "(no label)" : chosen);
    }

    private boolean isSuppressed(String stripped) {
        String norm = OutlineMarkers.normalizeHeading(stripped);
        return norm.equalsIgnoreCase(docTitleNorm)
                || runningTitles.contains(norm)
                || GENERIC_LABELS.contains(norm.toLowerCase(java.util.Locale.ROOT));
    }

    private String firstNonNull(String... values) {
        for (String v : values) {
            if (v != null) return v;
        }
        return null;
    }

    /** 문서 전체에서 여러 블록에 반복 등장하는 heading-like 줄(=running title)을 검출. */
    private java.util.Set<String> detectRunningTitles(List<Block> blocks) {
        java.util.Map<String, Integer> count = new java.util.HashMap<>();
        for (Block block : blocks) {
            java.util.Set<String> seen = new java.util.HashSet<>();
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
        java.util.Set<String> running = new java.util.HashSet<>();
        count.forEach((line, c) -> {
            if (c >= threshold) {
                running.add(line);
            }
        });
        return running;
    }

    private String clip(String s) {
        String one = s.replace("\n", " ").trim();
        return one.length() > LABEL_MAX ? one.substring(0, LABEL_MAX) + "…" : one;
    }

    // ---- 블록 분할 ----

    private List<Block> toBlocks(List<ExtractedSourceUnit> units) {
        List<Block> blocks = new ArrayList<>();
        for (ExtractedSourceUnit unit : units) {
            if (unit.text() == null || unit.text().isBlank()) {
                continue;
            }
            for (String paragraph : unit.text().split("\n{2,}")) {
                String p = paragraph.trim();
                if (p.isBlank()) {
                    continue;
                }
                List<String> parts = p.length() > MAX_BLOCK_CHARS ? splitByLength(p) : List.of(p);
                for (String part : parts) {
                    if (part.length() < MIN_BLOCK_CHARS && !blocks.isEmpty()) {
                        Block prev = blocks.remove(blocks.size() - 1);
                        blocks.add(new Block(prev.page(), prev.text() + "\n" + part));
                    } else {
                        blocks.add(new Block(unit.index(), part));
                    }
                }
            }
        }
        return blocks;
    }

    private List<String> splitByLength(String text) {
        List<String> out = new ArrayList<>();
        for (int start = 0; start < text.length(); start += MAX_BLOCK_CHARS) {
            out.add(text.substring(start, Math.min(start + MAX_BLOCK_CHARS, text.length())).trim());
        }
        return out;
    }

    // ---- 출력 ----

    private void printSections(String title, List<Block> blocks, double[] sims, List<Integer> boundaries) {
        System.out.printf("%n=== %s → %d sections ===%n", title, boundaries.size() + 1);
        List<Integer> cuts = new ArrayList<>(boundaries);
        cuts.add(blocks.size());
        int start = 0, order = 1;
        for (int cut : cuts) {
            int chars = 0;
            for (int i = start; i < cut; i++) {
                chars += blocks.get(i).text().length();
            }
            double trigger = start == 0 ? -1 : sims[start];
            System.out.printf("  S%-3d blocks[%d~%d] p%d~%d %5d자 %s label=%s%n",
                    order++, start, cut - 1, blocks.get(start).page(), blocks.get(cut - 1).page(), chars,
                    trigger < 0 ? "          " : String.format("(sim=%.3f)", trigger),
                    deriveLabel(blocks, start, cut));
            start = cut;
        }
    }

    private void printSimStats(double[] sims) {
        double[] sorted = new double[sims.length - 1];
        System.arraycopy(sims, 1, sorted, 0, sorted.length);
        java.util.Arrays.sort(sorted);
        double mean = mean(sims);
        System.out.printf("%n--- 인접 유사도 분포 (n=%d) ---%n", sorted.length);
        System.out.printf("  min=%.3f p10=%.3f p25=%.3f median=%.3f mean=%.3f max=%.3f std=%.3f%n",
                sorted[0], pct(sorted, 0.10), pct(sorted, 0.25), pct(sorted, 0.50),
                mean, sorted[sorted.length - 1], std(sims, mean));
    }

    private double pct(double[] sorted, double q) {
        return sorted[Math.min(sorted.length - 1, (int) Math.floor(q * sorted.length))];
    }

    private double mean(double[] sims) {
        double sum = 0;
        for (int i = 1; i < sims.length; i++) sum += sims[i];
        return sum / (sims.length - 1);
    }

    private double std(double[] sims, double mean) {
        double sum = 0;
        for (int i = 1; i < sims.length; i++) sum += (sims[i] - mean) * (sims[i] - mean);
        return Math.sqrt(sum / (sims.length - 1));
    }

    private String preview(String text, int limit) {
        String oneLine = text.replace("\n", " ⏎ ");
        return oneLine.length() > limit ? oneLine.substring(0, limit) + "…" : oneLine;
    }

    // ---- 임베딩 (디스크 캐시) ----

    private List<float[]> embedWithCache(Path pdfPath, List<Block> blocks) throws Exception {
        boolean noCache = Boolean.parseBoolean(System.getProperty("embed.nocache", "false"));
        Path cacheDir = Path.of("build/tmp/embed-cache");
        Path cacheFile = cacheDir.resolve(pdfPath.getFileName() + ".vec");

        if (!noCache && Files.exists(cacheFile)) {
            List<float[]> cached = loadCache(cacheFile, blocks.size());
            if (cached != null) {
                System.out.println("embed       : CACHE HIT " + cacheFile);
                return cached;
            }
        }

        String url = resolve("ollama.embed.url", "OLLAMA_EMBEDDING_URL", "http://localhost:11434");
        String model = resolve("ollama.embed.model", "OLLAMA_EMBEDDING_MODEL", "qwen3-embedding:0.6b");
        System.out.println("embed       : " + url + "  model=" + model + "  (캐시 미스 — 호출)");
        long t0 = System.currentTimeMillis();
        List<float[]> vectors = embedAll(blocks.stream().map(Block::text).toList(), url, model);
        System.out.printf("embedded %d blocks in %.1fs%n", vectors.size(), (System.currentTimeMillis() - t0) / 1000.0);

        Files.createDirectories(cacheDir);
        saveCache(cacheFile, vectors);
        return vectors;
    }

    private List<float[]> loadCache(Path file, int expectedCount) {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file.toFile())))) {
            int n = in.readInt();
            int dim = in.readInt();
            if (n != expectedCount) {
                return null; // 블록 수 바뀌면 무효
            }
            List<float[]> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                float[] v = new float[dim];
                for (int j = 0; j < dim; j++) v[j] = in.readFloat();
                out.add(v);
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private void saveCache(Path file, List<float[]> vectors) throws Exception {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file.toFile())))) {
            out.writeInt(vectors.size());
            out.writeInt(vectors.isEmpty() ? 0 : vectors.get(0).length);
            for (float[] v : vectors) {
                for (float f : v) out.writeFloat(f);
            }
        }
    }

    private List<float[]> embedAll(List<String> texts, String url, String model) throws Exception {
        List<float[]> out = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += EMBED_BATCH) {
            out.addAll(embedBatch(texts.subList(i, Math.min(i + EMBED_BATCH, texts.size())), url, model));
        }
        return out;
    }

    private List<float[]> embedBatch(List<String> batch, String url, String model) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("model", model, "input", batch));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "/api/embed"))
                .timeout(Duration.ofSeconds(180))
                .header("Content-Type", "application/json")
                .header("ngrok-skip-browser-warning", "1")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("임베딩 호출 실패 status=" + response.statusCode()
                    + " body=" + preview(response.body(), 200));
        }
        JsonNode embeddings = objectMapper.readTree(response.body()).get("embeddings");
        if (embeddings == null || !embeddings.isArray()) {
            throw new IllegalStateException("응답에 embeddings 없음: " + preview(response.body(), 200));
        }
        List<float[]> vectors = new ArrayList<>(embeddings.size());
        for (JsonNode vec : embeddings) {
            float[] v = new float[vec.size()];
            for (int j = 0; j < vec.size(); j++) v[j] = (float) vec.get(j).asDouble();
            vectors.add(v);
        }
        return vectors;
    }

    private double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return (na == 0 || nb == 0) ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    // ---- 설정 해석: 시스템 프로퍼티 → 환경변수 → .env → 기본값 ----

    private String resolve(String sysProp, String envKey, String fallback) {
        String v = System.getProperty(sysProp);
        if (v != null && !v.isBlank()) return v.trim();
        v = System.getenv(envKey);
        if (v != null && !v.isBlank()) return v.trim();
        v = fromDotEnv(envKey);
        if (v != null && !v.isBlank()) return v.trim();
        return fallback;
    }

    private String fromDotEnv(String key) {
        try {
            Path env = Path.of(".env");
            if (!Files.exists(env)) return null;
            for (String line : Files.readAllLines(env)) {
                String l = line.trim();
                if (l.startsWith(key + "=")) {
                    return l.substring(key.length() + 1).replace("\"", "").replace("'", "").trim();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
