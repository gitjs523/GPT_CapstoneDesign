package org.example.snow.document.application;

import org.example.snow.document.domain.ExtractedDocument;
import org.example.snow.document.domain.ExtractedSourceUnit;
import org.example.snow.document.infra.extractor.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 실제 PDF의 페이지별 원문을 그대로 덤프해 "문단 분리가 가능한 구조인지" 눈으로 확인하는 진단 테스트.
 *
 * 실행:
 *   GRADLE_USER_HOME=/tmp/gradle-home ./gradlew test \
 *     --tests '*PageTextDumpTest' -Dmeasure.pdf=build/tmp/test/cloud02_2_EC2.pptx.pdf
 */
@EnabledIfSystemProperty(named = "measure.pdf", matches = ".+")
class PageTextDumpTest {

    private final PdfTextExtractor pdfTextExtractor = new PdfTextExtractor();
    private final TextPreprocessor textPreprocessor = new TextPreprocessor();

    @Test
    void dumpPages1to8() throws Exception {
        Path pdfPath = Path.of(System.getProperty("measure.pdf"));
        byte[] content = Files.readAllBytes(pdfPath);

        ExtractedDocument extracted = pdfTextExtractor.extract(
                new UploadedDocument(pdfPath.getFileName().toString(), "application/pdf", content));

        List<ExtractedSourceUnit> units = extracted.sourceUnits();

        for (int i = 0; i < Math.min(8, units.size()); i++) {
            ExtractedSourceUnit unit = units.get(i);
            String normalized = textPreprocessor.normalize(unit.text());

            System.out.println("\n╔══════════════ PAGE " + unit.index()
                    + " (heading=" + unit.heading() + ") ══════════════");

            // 1) 줄바꿈 구조를 눈으로 보기 위해 \n=⏎, 빈 줄(문단 경계)=⟦¶⟧ 로 표시
            System.out.println("--- [RAW: ⏎=줄바꿈, ⟦¶⟧=빈줄(문단경계 후보)] ---");
            String visible = normalized
                    .replace("\n\n", " ⟦¶⟧\n")
                    .replace("\n", "⏎\n");
            System.out.println(visible);

            // 2) 문단 분리 시도 (ChunkComposer와 동일: \n{2,} 기준)
            String[] paragraphs = normalized.split("\\n{2,}");
            System.out.println("--- [문단 분리 결과: \\n{2,} 기준 → " + paragraphs.length + "개] ---");
            for (int p = 0; p < paragraphs.length; p++) {
                String para = paragraphs[p].replace("\n", " ").trim();
                String preview = para.length() > 70 ? para.substring(0, 70) + "…" : para;
                System.out.printf("  [P%d, %d자] %s%n", p + 1, paragraphs[p].length(), preview);
            }

            // 3) 줄 단위 통계 (문단이 단일 줄로 뭉쳐 나오는지 확인용)
            long lineCount = normalized.lines().count();
            long blankSeparators = countOccurrences(normalized, "\n\n");
            System.out.printf("--- [통계] 총 %d자 / 줄 %d개 / 빈줄(문단경계) %d개%n",
                    normalized.length(), lineCount, blankSeparators);
        }
        System.out.println("\n════════════════════ END ════════════════════\n");
    }

    private long countOccurrences(String text, String token) {
        long count = 0;
        int idx = 0;
        while ((idx = text.indexOf(token, idx)) != -1) {
            count++;
            idx += token.length();
        }
        return count;
    }
}
