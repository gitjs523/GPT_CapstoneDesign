package org.example.snow.document.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.snow.document.application.chunking.BoilerplateFilter;
import org.example.snow.document.domain.ExtractedDocument;
import org.example.snow.document.domain.ExtractedSourceUnit;
import org.example.snow.document.infra.extractor.PdfTextExtractor;
import org.example.snow.global.queue.ModelQueueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 임베딩 처리량 측정 하니스 (ModelQueue 도입 "전" baseline = 큐 우회, 임베딩 엔드포인트 직접 호출).
 *
 * 목적: ModelQueue 정책의 1차 측정(2026-05-26, 합성 텍스트)을 실제 PDF chunk로 재측정한다.
 *   결과 정리: docs/modelqueue-policy.md
 *   - 청크는 본 파이프라인과 동일하게 추출(PdfTextExtractor)→전처리(TextPreprocessor)→boilerplate 제거→
 *     문단 블록 분할(빈 줄 경계, 40~600자)로 생성한다. SemanticSectionPrototypeTest와 같은 청킹이다.
 *   - 임베딩 캐시를 쓰지 않는다(시간 측정이 목적).
 *
 * 측정 차원(사용자 요청): (1) 큐 우회 baseline, (2) 배치 사이즈 sweep, (3) 다중 문서/동시성.
 *
 * 실행:
 *   GRADLE_USER_HOME=/tmp/gradle-home ./gradlew test --tests '*EmbeddingThroughputMeasurementTest' \
 *     -Dmeasure.embedding=1 -Dmeasure.mode=inventory --rerun-tasks
 *   모드: -Dmeasure.mode=inventory | batch | concurrency
 *   옵션: -Dmeasure.dir=build/tmp/test  -Dmeasure.pdf=경제학개론_전반기.pdf
 *         -Dbatch.sizes=1,8,16,20,32,50  -Dconc.levels=1,2,3  -Dconc.batch=20
 *         -Dollama.embed.url=...  -Dollama.embed.model=...
 */
@EnabledIfSystemProperty(named = "measure.embedding", matches = ".+")
class EmbeddingThroughputMeasurementTest {

    private static final int MIN_BLOCK_CHARS = 40;
    private static final int MAX_BLOCK_CHARS = 600;

    private final PdfTextExtractor extractor = new PdfTextExtractor();
    private final TextPreprocessor preprocessor = new TextPreprocessor();
    private final BoilerplateFilter boilerplateFilter = new BoilerplateFilter();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    @Test
    void measure() throws Exception {
        String mode = System.getProperty("measure.mode", "inventory");
        Path dir = Path.of(System.getProperty("measure.dir", "build/tmp/test"));
        List<Path> pdfs = listPdfs(dir);

        System.out.println("\n################ EMBEDDING THROUGHPUT MEASUREMENT ################");
        System.out.println("mode   : " + mode);
        System.out.println("dir    : " + dir.toAbsolutePath());
        System.out.println("pdfs   : " + pdfs.size());

        switch (mode) {
            case "inventory" -> inventory(pdfs);
            case "batch" -> batchSweep(pdfs);
            case "concurrency" -> concurrency(pdfs);
            case "queued" -> queued(pdfs);
            default -> System.out.println("알 수 없는 모드: " + mode);
        }
        System.out.println("#################################################################\n");
    }

    // ---- Phase A: 청크 인벤토리 (임베딩 없음, 규모 파악) ----

    private void inventory(List<Path> pdfs) throws Exception {
        System.out.printf("%n%-34s %6s %7s %9s %6s %7s %6s  %s%n",
                "file", "pages", "chunks", "chars", "min", "median", "max", "batches@20");
        int totalChunks = 0;
        for (Path pdf : pdfs) {
            List<String> chunks = extractChunks(pdf);
            int[] stat = charStats(chunks);
            int sumChars = chunks.stream().mapToInt(String::length).sum();
            int batches20 = (int) Math.ceil(chunks.size() / 20.0);
            totalChunks += chunks.size();
            System.out.printf("%-34s %6d %7d %9d %6d %7d %6d  %d%n",
                    clip(pdf.getFileName().toString(), 34), pageCount(pdf), chunks.size(),
                    sumChars, stat[0], stat[1], stat[2], batches20);
        }
        System.out.printf("%n총 chunk 수: %d  (배치20 기준 총 %d 호출)%n", totalChunks, (int) Math.ceil(totalChunks / 20.0));
        System.out.println("\n참고: 옛 측정(05-26)은 합성 텍스트 20개×10배치=문서1개(약200chunk) 가정.");
    }

    // ---- Phase B: 배치 사이즈 sweep (단일 문서, 순차, 큐 우회) ----

    private void batchSweep(List<Path> pdfs) throws Exception {
        Path pdf = pickPdf(pdfs);
        List<String> chunks = extractChunks(pdf);
        List<Integer> sizes = parseInts(System.getProperty("batch.sizes", "1,8,16,20,32,50"));
        String url = embedUrl(), model = embedModel();

        System.out.println("\n[BATCH SWEEP] file=" + pdf.getFileName() + "  chunks=" + chunks.size());
        System.out.println("endpoint=" + url + "  model=" + model);
        warmup(url, model);

        System.out.printf("%n%8s %8s %12s %10s %14s%n", "batch", "calls", "total(s)", "avg/call", "throughput");
        for (int size : sizes) {
            Result r = embedSequential(chunks, size, url, model);
            System.out.printf("%8d %8d %12.2f %9.2fs %11.1f c/s%n",
                    size, r.calls, r.totalMs / 1000.0, (r.totalMs / 1000.0) / r.calls, chunks.size() / (r.totalMs / 1000.0));
        }
    }

    // ---- Phase C: 다중 문서 / 동시성 (큐 우회 baseline = E2/E3/E4 재현) ----

    private void concurrency(List<Path> pdfs) throws Exception {
        Path pdf = pickPdf(pdfs);
        List<String> chunks = extractChunks(pdf);
        int batch = Integer.parseInt(System.getProperty("conc.batch", "20"));
        List<Integer> levels = parseInts(System.getProperty("conc.levels", "1,2,3"));
        String url = embedUrl(), model = embedModel();

        System.out.println("\n[CONCURRENCY] file=" + pdf.getFileName() + "  chunks=" + chunks.size() + "  batch=" + batch);
        System.out.println("endpoint=" + url + "  model=" + model);
        System.out.println("각 동시 사용자는 같은 문서(동일 워크로드)를 임베딩한다. 큐 우회 = 직접 동시 호출.");
        warmup(url, model);

        double baseSeconds = 0;
        for (int level : levels) {
            ExecutorService pool = Executors.newFixedThreadPool(level);
            List<Callable<Result>> tasks = new ArrayList<>();
            for (int u = 0; u < level; u++) {
                tasks.add(() -> embedSequential(chunks, batch, url, model));
            }
            long wallStart = System.currentTimeMillis();
            List<Future<Result>> futures = pool.invokeAll(tasks);
            long wallMs = System.currentTimeMillis() - wallStart;
            List<Double> perUser = new ArrayList<>();
            for (Future<Result> f : futures) {
                perUser.add(f.get().totalMs / 1000.0);
            }
            pool.shutdown();

            double avgUser = perUser.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            if (level == 1) {
                baseSeconds = avgUser;
            }
            System.out.printf("%n--- 동시 %d명 ---%n", level);
            for (int u = 0; u < perUser.size(); u++) {
                System.out.printf("  user%d : %6.1fs  (baseline 대비 %.2f배)%n",
                        u + 1, perUser.get(u), baseSeconds == 0 ? 1 : perUser.get(u) / baseSeconds);
            }
            System.out.printf("  wall  : %6.1fs   평균 user %.1fs   평균 배율 %.2f배%n",
                    wallMs / 1000.0, avgUser, baseSeconds == 0 ? 1 : avgUser / baseSeconds);
        }
    }

    // ---- Phase D: 큐 경유 (ModelQueue 도입 "후" = 단일 스레드 직렬화) ----

    private void queued(List<Path> pdfs) throws Exception {
        Path pdf = pickPdf(pdfs);
        List<String> chunks = extractChunks(pdf);
        int batch = Integer.parseInt(System.getProperty("conc.batch", "20"));
        List<Integer> levels = parseInts(System.getProperty("conc.levels", "1,2,3"));
        String url = embedUrl(), model = embedModel();

        System.out.println("\n[QUEUED] file=" + pdf.getFileName() + "  chunks=" + chunks.size() + "  batch=" + batch);
        System.out.println("endpoint=" + url + "  model=" + model);
        System.out.println("N명이 동시에 도착해 ModelQueueService(임베딩 단일 스레드)에 등록 → 직렬 처리.");
        warmup(url, model);

        double baseProc = 0;
        for (int level : levels) {
            ModelQueueService queue = new ModelQueueService(Math.max(level, 5), 10);
            List<long[]> records = Collections.synchronizedList(new ArrayList<>()); // [uid, submitMs, startMs, endMs]
            CountDownLatch done = new CountDownLatch(level);

            long t0 = System.currentTimeMillis();
            for (int u = 0; u < level; u++) {
                final int uid = u;
                final long submitMs = System.currentTimeMillis() - t0;
                boolean accepted = queue.submitEmbedding(() -> {
                    long startMs = System.currentTimeMillis() - t0;
                    try {
                        embedSequential(chunks, batch, url, model);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    long endMs = System.currentTimeMillis() - t0;
                    records.add(new long[]{uid, submitMs, startMs, endMs});
                    done.countDown();
                });
                if (!accepted) {
                    System.out.println("  user" + uid + " 큐 정원 초과로 거부됨 (ModelQueueFull)");
                    done.countDown();
                }
            }
            done.await();
            long wallMs = System.currentTimeMillis() - t0;
            queue.shutdown();

            records.sort((a, b) -> Long.compare(a[3], b[3])); // 종료 순
            System.out.printf("%n--- 큐 경유 동시 %d명 ---%n", level);
            double procSum = 0, firstDone = Double.MAX_VALUE;
            for (long[] r : records) {
                double wait = (r[2] - r[1]) / 1000.0;
                double proc = (r[3] - r[2]) / 1000.0;
                double total = (r[3] - r[1]) / 1000.0;
                procSum += proc;
                firstDone = Math.min(firstDone, r[3] / 1000.0);
                System.out.printf("  user%d : 대기 %6.1fs + 처리 %6.1fs = 완료 %6.1fs%n",
                        r[0], wait, proc, total);
            }
            double avgProc = records.isEmpty() ? 0 : procSum / records.size();
            if (level == 1) {
                baseProc = avgProc;
            }
            System.out.printf("  wall %6.1fs  | 첫 완료 %.1fs | 평균 처리시간 %.1fs (baseline 대비 %.2f배)%n",
                    wallMs / 1000.0, firstDone, avgProc, baseProc == 0 ? 1 : avgProc / baseProc);
        }
    }

    // ---- 임베딩 호출 ----

    private record Result(int calls, long totalMs) {
    }

    private Result embedSequential(List<String> texts, int batchSize, String url, String model) throws Exception {
        int calls = 0;
        long start = System.currentTimeMillis();
        for (int i = 0; i < texts.size(); i += batchSize) {
            embedBatch(texts.subList(i, Math.min(i + batchSize, texts.size())), url, model);
            calls++;
        }
        return new Result(calls, System.currentTimeMillis() - start);
    }

    private void warmup(String url, String model) throws Exception {
        embedBatch(List.of("워밍업 호출 — 모델 로드 시간 제거"), url, model);
        System.out.println("warmup : done");
    }

    private void embedBatch(List<String> batch, String url, String model) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("model", model, "input", batch));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "/api/embed"))
                .timeout(Duration.ofSeconds(300))
                .header("Content-Type", "application/json")
                .header("ngrok-skip-browser-warning", "1")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("임베딩 호출 실패 status=" + response.statusCode()
                    + " body=" + clip(response.body(), 200));
        }
        JsonNode embeddings = objectMapper.readTree(response.body()).get("embeddings");
        if (embeddings == null || !embeddings.isArray() || embeddings.size() != batch.size()) {
            throw new IllegalStateException("응답 embeddings 개수 불일치: " + clip(response.body(), 200));
        }
    }

    // ---- 청크 추출 (본 파이프라인과 동일) ----

    private List<String> extractChunks(Path pdf) throws Exception {
        ExtractedDocument extracted = extractor.extract(
                new UploadedDocument(pdf.getFileName().toString(), "application/pdf", Files.readAllBytes(pdf)));
        List<ExtractedSourceUnit> normalized = extracted.sourceUnits().stream()
                .map(u -> new ExtractedSourceUnit(u.index(), u.heading(), preprocessor.normalize(u.text())))
                .toList();
        BoilerplateFilter.Result filtered = boilerplateFilter.filter(normalized);
        return toBlocks(filtered.units());
    }

    private List<String> toBlocks(List<ExtractedSourceUnit> units) {
        List<String> blocks = new ArrayList<>();
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
                        blocks.set(blocks.size() - 1, blocks.get(blocks.size() - 1) + "\n" + part);
                    } else {
                        blocks.add(part);
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

    private int pageCount(Path pdf) throws Exception {
        ExtractedDocument extracted = extractor.extract(
                new UploadedDocument(pdf.getFileName().toString(), "application/pdf", Files.readAllBytes(pdf)));
        return extracted.sourceUnits().size();
    }

    // ---- 유틸 ----

    private int[] charStats(List<String> chunks) {
        if (chunks.isEmpty()) {
            return new int[]{0, 0, 0};
        }
        int[] lens = chunks.stream().mapToInt(String::length).sorted().toArray();
        return new int[]{lens[0], lens[lens.length / 2], lens[lens.length - 1]};
    }

    private List<Path> listPdfs(Path dir) throws Exception {
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".pdf"))
                    .sorted().toList();
        }
    }

    private Path pickPdf(List<Path> pdfs) {
        String name = System.getProperty("measure.pdf");
        if (name != null) {
            return pdfs.stream().filter(p -> p.getFileName().toString().equals(name)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("PDF 없음: " + name));
        }
        return pdfs.get(0);
    }

    private List<Integer> parseInts(String csv) {
        return Arrays.stream(csv.split(",")).map(String::trim).map(Integer::parseInt).toList();
    }

    private String clip(String s, int max) {
        String one = s.replace("\n", " ").trim();
        return one.length() > max ? one.substring(0, max) + "…" : one;
    }

    private String clip(String s) {
        return clip(s, 200);
    }

    // ---- 설정 해석: 시스템 프로퍼티 → 환경변수 → .env → 기본값 ----

    private String embedUrl() {
        return resolve("ollama.embed.url", "OLLAMA_EMBEDDING_URL", "http://localhost:11434");
    }

    private String embedModel() {
        return resolve("ollama.embed.model", "OLLAMA_EMBEDDING_MODEL", "qwen3-embedding:0.6b");
    }

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
