package org.example.snow.document.application;

import org.example.snow.document.application.chunking.BoilerplateFilter;
import org.example.snow.document.application.chunking.ChunkComposer;
import org.example.snow.document.application.chunking.DocumentTitleResolver;
import org.example.snow.document.application.chunking.SectionBuilder;
import org.example.snow.document.domain.ExtractedChunk;
import org.example.snow.document.domain.ExtractedSection;
import org.example.snow.document.infra.extractor.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * 실제 PDF로 SourceUnit → Section → Chunk 크기 분포를 측정하는 일회성 진단 테스트.
 *
 * 실제 DocumentIngestionService.ingest()를 그대로 호출하므로 파이프라인 변경(전처리·청킹)을
 * 자동으로 반영한다.
 *
 * 실행:
 *   GRADLE_USER_HOME=/tmp/gradle-home ./gradlew test \
 *     --tests '*ChunkSizeMeasurementTest' -Dmeasure.pdf=build/tmp/test/cloud02_2_EC2.pptx.pdf --rerun-tasks
 */
@EnabledIfSystemProperty(named = "measure.pdf", matches = ".+")
class ChunkSizeMeasurementTest {

    private static final int MAX_CHUNK_LENGTH = 1_600; // ChunkComposer 상수와 동일

    private final DocumentIngestionService ingestionService = new DocumentIngestionService(
            List.of(new PdfTextExtractor()),
            new TextPreprocessor(),
            new ChunkingService(new SectionBuilder(), new ChunkComposer()),
            new BoilerplateFilter(),
            new DocumentTitleResolver()
    );

    @Test
    void measure() throws Exception {
        Path pdfPath = Path.of(System.getProperty("measure.pdf"));
        byte[] content = Files.readAllBytes(pdfPath);

        DocumentUploadCommand command = new DocumentUploadCommand(
                new UploadedDocument(pdfPath.getFileName().toString(), "application/pdf", content)
        );
        DocumentProcessingResult result = ingestionService.ingest(command);

        List<ExtractedSection> sections = result.sections();
        List<ExtractedChunk> chunks = result.chunks();

        System.out.println("\n================ CHUNK SIZE MEASUREMENT ================");
        System.out.println("file            : " + pdfPath.getFileName());
        System.out.println("sourceUnits     : " + result.sourceUnitCount() + " (PDF 페이지 수)");

        List<Integer> sectionLens = sections.stream().map(s -> s.text().length()).sorted().toList();
        List<Integer> chunkLens = chunks.stream().map(c -> c.text().length()).sorted().toList();

        printStats("SECTION", sectionLens);
        printStats("CHUNK", chunkLens);

        long sectionsOverMax = sectionLens.stream().filter(l -> l > MAX_CHUNK_LENGTH).count();
        System.out.printf("%n1,600자 초과 Section : %d / %d (%.1f%%)%n",
                sectionsOverMax, sectionLens.size(),
                sectionLens.isEmpty() ? 0.0 : 100.0 * sectionsOverMax / sectionLens.size());
        System.out.printf("Chunk 수 / Section 수 = %d / %d = %.2f배%n",
                chunks.size(), sections.size(),
                sections.isEmpty() ? 0.0 : (double) chunks.size() / sections.size());

        System.out.println("\n--- 상위 25개 Section (길이 내림차순) / 그 Section이 만든 Chunk 수 ---");
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
        System.out.printf("%-8s count=%-4d min=%-5d median=%-5d avg=%-7.1f max=%-6d (자)%n",
                label, n, sortedLens.get(0), sortedLens.get(n / 2), (double) sum / n, sortedLens.get(n - 1));
    }
}
