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
import java.util.List;

/**
 * 재구성한 청킹 전략으로 실제 Section / Chunk가 어떻게 분류되는지 본문까지 덤프하는 진단 테스트.
 * 임베딩에 들어갈 breadcrumb 헤더도 함께 출력한다.
 *
 * 실행:
 *   GRADLE_USER_HOME=/tmp/gradle-home ./gradlew test --tests '*ChunkInspectionTest' \
 *     -Dmeasure.pdf=build/tmp/test/cloud02_1_IAM.pptx.pdf --rerun-tasks
 *   (선택) -Dinspect.maxSections=6 로 출력 섹션 수 제한, -Dinspect.body=200 로 본문 미리보기 길이
 */
@EnabledIfSystemProperty(named = "measure.pdf", matches = ".+")
class ChunkInspectionTest {

    private final DocumentIngestionService ingestionService = new DocumentIngestionService(
            List.of(new PdfTextExtractor()),
            new TextPreprocessor(),
            new ChunkingService(new SectionBuilder(), new ChunkComposer()),
            new BoilerplateFilter(),
            new DocumentTitleResolver()
    );

    @Test
    void inspect() throws Exception {
        Path pdfPath = Path.of(System.getProperty("measure.pdf"));
        int maxSections = Integer.parseInt(System.getProperty("inspect.maxSections", "8"));
        int bodyPreview = Integer.parseInt(System.getProperty("inspect.body", "160"));

        DocumentUploadCommand command = new DocumentUploadCommand(
                new UploadedDocument(pdfPath.getFileName().toString(), "application/pdf",
                        Files.readAllBytes(pdfPath)));
        DocumentProcessingResult result = ingestionService.ingest(command);

        System.out.println("\n################ CHUNK INSPECTION ################");
        System.out.println("file     : " + pdfPath.getFileName());
        System.out.println("docTitle : " + result.docTitle());
        System.out.println("sections : " + result.sections().size() + " / chunks : " + result.chunks().size());

        List<ExtractedSection> sections = result.sections();
        List<ExtractedChunk> chunks = result.chunks();

        int shown = Math.min(maxSections, sections.size());
        for (int i = 0; i < shown; i++) {
            ExtractedSection s = sections.get(i);
            System.out.printf("%n┌─ SECTION %d  [pages %d~%d, %d자]  heading=\"%s\"%n",
                    i + 1, s.sourceStartIndex(), s.sourceEndIndex(), s.text().length(), s.heading());
            System.out.println("│  body: " + preview(s.text(), bodyPreview));

            List<ExtractedChunk> childChunks = chunks.stream()
                    .filter(c -> sameOrigin(c, s) && c.heading().startsWith(stripPart(s.heading())))
                    .toList();
            for (ExtractedChunk c : childChunks) {
                System.out.printf("│    └ CHUNK [%d자] heading=\"%s\"%n", c.text().length(), c.heading());
                System.out.println("│        임베딩입력: " + preview(embeddingHeader(result.docTitle(), c) + c.text(), bodyPreview));
            }
        }
        if (sections.size() > shown) {
            System.out.println("\n... (" + (sections.size() - shown) + "개 Section 생략)");
        }
        System.out.println("#################################################\n");
    }

    private boolean sameOrigin(ExtractedChunk c, ExtractedSection s) {
        return c.sourceStartIndex() == s.sourceStartIndex() && c.sourceEndIndex() == s.sourceEndIndex();
    }

    private String stripPart(String heading) {
        int idx = heading.indexOf(" (Part ");
        return idx >= 0 ? heading.substring(0, idx) : heading;
    }

    private String embeddingHeader(String docTitle, ExtractedChunk c) {
        StringBuilder h = new StringBuilder();
        if (docTitle != null && !docTitle.isBlank()) h.append("[문서: ").append(docTitle).append("] ");
        if (c.heading() != null && !c.heading().isBlank()) h.append("[섹션: ").append(stripPart(c.heading())).append("] ");
        return h.toString();
    }

    private String preview(String text, int limit) {
        String oneLine = text.replace("\n", " ⏎ ");
        return oneLine.length() > limit ? oneLine.substring(0, limit) + " …" : oneLine;
    }
}
