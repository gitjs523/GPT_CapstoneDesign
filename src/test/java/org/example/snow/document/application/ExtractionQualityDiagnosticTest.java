package org.example.snow.document.application;

import org.example.snow.document.domain.ExtractedDocument;
import org.example.snow.document.domain.ExtractedSourceUnit;
import org.example.snow.document.infra.extractor.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PDF 추출 품질 진단: 공백이 깨져 보이는 '?'의 실제 정체(코드포인트)를 확인한다.
 *
 * 실행:
 *   GRADLE_USER_HOME=/tmp/gradle-home ./gradlew test \
 *     --tests '*ExtractionQualityDiagnosticTest' \
 *     -Dmeasure.pdf=build/tmp/test/경제학개론_전반기.pdf --rerun-tasks
 */
@EnabledIfSystemProperty(named = "measure.pdf", matches = ".+")
class ExtractionQualityDiagnosticTest {

    private final PdfTextExtractor pdfTextExtractor = new PdfTextExtractor();

    @Test
    void diagnoseFirstPage() throws Exception {
        Path pdfPath = Path.of(System.getProperty("measure.pdf"));
        ExtractedDocument extracted = pdfTextExtractor.extract(
                new UploadedDocument(pdfPath.getFileName().toString(), "application/pdf",
                        Files.readAllBytes(pdfPath)));

        ExtractedSourceUnit page1 = extracted.sourceUnits().get(0);
        String text = page1.text();

        System.out.println("\n========== EXTRACTION QUALITY DIAGNOSTIC ==========");
        System.out.println("file: " + pdfPath.getFileName());

        // 1) 첫 120자를 [문자 U+코드포인트] 형태로 출력
        System.out.println("\n--- 첫 120자 코드포인트 ---");
        StringBuilder line = new StringBuilder();
        int shown = 0;
        for (int i = 0; i < text.length() && shown < 120; i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                line.append("[\\n] ");
            } else {
                line.append(c).append("(U+").append(String.format("%04X", (int) c)).append(") ");
            }
            shown++;
            if (line.length() > 110) {
                System.out.println(line);
                line.setLength(0);
            }
        }
        if (line.length() > 0) System.out.println(line);

        // 2) 전체 페이지의 문자 종류별 빈도 (공백류 / '?' 집중 분석)
        System.out.println("\n--- 의심 문자 빈도 (페이지 1 전체) ---");
        Map<String, Integer> counts = new LinkedHashMap<>();
        int questionMark = 0;   // U+003F
        int space = 0;          // U+0020
        int nbsp = 0;           // U+00A0
        int otherWeird = 0;     // 그 외 제어/비정상
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '?') questionMark++;
            else if (c == ' ') space++;
            else if (c == ' ') nbsp++;
            else if (c < 0x20 && c != '\n' && c != '\t') otherWeird++;
        }
        counts.put("'?' (U+003F)", questionMark);
        counts.put("space (U+0020)", space);
        counts.put("nbsp (U+00A0)", nbsp);
        counts.put("기타 제어문자", otherWeird);
        counts.put("전체 길이", text.length());
        counts.forEach((k, v) -> System.out.printf("  %-18s : %d%n", k, v));

        // 3) '?' 주변 문맥 (정말 공백 자리인지 확인)
        System.out.println("\n--- '?' 등장 위치 주변 (앞뒤 1자) ---");
        int printed = 0;
        for (int i = 0; i < text.length() && printed < 10; i++) {
            if (text.charAt(i) == '?') {
                char prev = i > 0 ? text.charAt(i - 1) : ' ';
                char next = i + 1 < text.length() ? text.charAt(i + 1) : ' ';
                System.out.printf("  ...%c[?]%c...  (prev=U+%04X, next=U+%04X)%n",
                        prev, next, (int) prev, (int) next);
                printed++;
            }
        }
        System.out.println("===================================================\n");
    }
}
