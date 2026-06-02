package org.example.snow.document.application;

import org.example.snow.document.application.chunking.ChunkComposer;
import org.example.snow.document.application.chunking.ChunkStrategyResolver;
import org.example.snow.document.application.chunking.SectionBuilder;
import org.example.snow.document.domain.ChunkStrategy;
import org.example.snow.document.domain.ExtractedChunk;
import org.example.snow.document.domain.ExtractedDocument;
import org.example.snow.document.domain.ExtractedSection;
import org.example.snow.document.domain.ExtractedSourceUnit;
import org.example.snow.document.infra.extractor.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 실제 PDF로 SourceUnit → Section → Chunk 크기 분포를 측정하는 일회성 진단 테스트.
 *
 * 실행:
 *   GRADLE_USER_HOME=/tmp/gradle-home ./gradlew test \
 *     --tests '*ChunkSizeMeasurementTest' -Dmeasure.pdf=build/tmp/test/cloud02_2_EC2.pptx.pdf
 *
 * -Dmeasure.pdf 시스템 프로퍼티가 없으면 비활성(스킵)된다.
 */
@EnabledIfSystemProperty(named = "measure.pdf", matches = ".+")
class ChunkSizeMeasurementTest {

    private static final int MAX_CHUNK_LENGTH = 1_600; // ChunkComposer 상수와 동일

    private final PdfTextExtractor pdfTextExtractor = new PdfTextExtractor();
    private final TextPreprocessor textPreprocessor = new TextPreprocessor();
    private final ChunkingService chunkingService = new ChunkingService(
            new ChunkStrategyResolver(),
            new SectionBuilder(),
            new ChunkComposer()
    );

    @Test
    void measure() throws Exception {
        Path pdfPath = Path.of(System.getProperty("measure.pdf"));
        byte[] content = Files.readAllBytes(pdfPath);

        UploadedDocument uploaded = new UploadedDocument(
                pdfPath.getFileName().toString(), "application/pdf", content);

        // 1) 추출
        ExtractedDocument extracted = pdfTextExtractor.extract(uploaded);

        // 2) 전처리 (DocumentIngestionService와 동일하게 sourceUnit별 normalize)
        List<ExtractedSourceUnit> normalizedUnits = extracted.sourceUnits().stream()
                .map(u -> new ExtractedSourceUnit(u.index(), u.heading(), textPreprocessor.normalize(u.text())))
                .toList();
        ExtractedDocument preprocessed = extracted.withSourceUnits(normalizedUnits);

        // 3) Section / Chunk (기본 AUTO → SECTION)
        ChunkStrategy strategy = chunkingService.resolveStrategy(preprocessed.sourceType(), ChunkStrategy.AUTO);
        List<ExtractedSection> sections = chunkingService.buildSections(preprocessed);
        List<ExtractedChunk> chunks = chunkingService.chunk(preprocessed, sections, strategy);

        // ── 출력 ──────────────────────────────────────────────
        System.out.println("\n================ CHUNK SIZE MEASUREMENT ================");
        System.out.println("file            : " + pdfPath.getFileName());
        System.out.println("strategy        : " + strategy);
        System.out.println("sourceUnits     : " + preprocessed.sourceUnits().size() + " (PDF 페이지 수)");

        List<Integer> sectionLens = sections.stream().map(s -> s.text().length()).sorted().toList();
        List<Integer> chunkLens = chunks.stream().map(c -> c.text().length()).sorted().toList();

        printStats("SECTION", sectionLens);
        printStats("CHUNK", chunkLens);

        long sectionsOverMax = sectionLens.stream().filter(l -> l > MAX_CHUNK_LENGTH).count();
        System.out.printf("%n1,600자 초과 Section : %d / %d (%.1f%%)  ← 이 경우에만 Section이 여러 Chunk로 쪼개진다%n",
                sectionsOverMax, sectionLens.size(),
                sectionLens.isEmpty() ? 0.0 : 100.0 * sectionsOverMax / sectionLens.size());
        System.out.printf("Chunk 수 / Section 수 = %d / %d = %.2f배%n",
                chunks.size(), sections.size(),
                sections.isEmpty() ? 0.0 : (double) chunks.size() / sections.size());

        System.out.println("\n--- Section별 길이 / 그 Section이 만든 Chunk 수 ---");
        sections.stream()
                .sorted(Comparator.comparingInt((ExtractedSection s) -> s.text().length()).reversed())
                .limit(25)
                .forEach(s -> {
                    long childChunks = chunks.stream()
                            .filter(c -> c.sourceStartIndex() == s.sourceStartIndex()
                                    && c.sourceEndIndex() == s.sourceEndIndex())
                            .count();
                    String heading = s.heading().length() > 30 ? s.heading().substring(0, 30) + "…" : s.heading();
                    System.out.printf("  len=%-6d chunks=%-3d pages=%d~%d  heading=%s%n",
                            s.text().length(), childChunks, s.sourceStartIndex(), s.sourceEndIndex(), heading);
                });
        System.out.println("========================================================\n");
    }

    private void printStats(String label, List<Integer> sortedLens) {
        if (sortedLens.isEmpty()) {
            System.out.printf("%-8s count=0%n", label);
            return;
        }
        int n = sortedLens.size();
        long sum = sortedLens.stream().mapToLong(Integer::longValue).sum();
        int min = sortedLens.get(0);
        int max = sortedLens.get(n - 1);
        int median = sortedLens.get(n / 2);
        double avg = (double) sum / n;
        System.out.printf("%-8s count=%-4d min=%-5d median=%-5d avg=%-7.1f max=%-6d (자)%n",
                label, n, min, median, avg, max);
    }
}
