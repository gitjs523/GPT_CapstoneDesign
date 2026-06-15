package org.example.snow.document.infra.extractor;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.example.snow.document.application.UploadedDocument;
import org.example.snow.document.domain.ExtractedDocument;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PdfTextExtractorOcrFallbackTest {

    @Test
    void usesOcrWhenPdfPageTextIsTooShort() throws Exception {
        PdfTextExtractor extractor = new PdfTextExtractor(
                image -> "OCR extracted text",
                new OllamaOcrProperties(
                        true,
                        "glm-ocr:latest",
                        "http://localhost:11434",
                        5,
                        180,
                        30,
                        72,
                        false,
                        "Text Recognition:"
                )
        );

        ExtractedDocument document = extractor.extract(new UploadedDocument(
                "scan.pdf",
                "application/pdf",
                blankPdf()
        ));

        assertThat(document.sourceUnits()).hasSize(1);
        assertThat(document.sourceUnits().get(0).text()).isEqualTo("OCR extracted text");
    }

    @Test
    void appendsVisualAnalysisWhenPdfVisionIsEnabled() throws Exception {
        PdfTextExtractor extractor = new PdfTextExtractor(
                image -> "",
                OllamaOcrProperties.disabled(),
                image -> "이 페이지는 검색 기반 생성 흐름을 설명하는 시각 자료입니다.",
                new OllamaVisionProperties(
                        true,
                        "qwen3-vl:4b",
                        "http://localhost:11434",
                        5,
                        180,
                        72,
                        "always",
                        true,
                        false,
                        "Analyze:"
                )
        );

        ExtractedDocument document = extractor.extract(new UploadedDocument(
                "lecture.pdf",
                "application/pdf",
                blankPdf()
        ));

        assertThat(document.sourceUnits()).hasSize(1);
        assertThat(document.sourceUnits().get(0).text())
                .contains("[시각 자료 설명]")
                .contains("검색 기반 생성 흐름");
    }

    @Test
    void imageMode_runsVisionOnlyWhenPdfPageHasImage() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        PdfTextExtractor extractor = new PdfTextExtractor(
                image -> "",
                OllamaOcrProperties.disabled(),
                image -> {
                    calls.incrementAndGet();
                    return "이미지 안의 도식 설명";
                },
                visionProperties("image")
        );

        // 이미지 없는 페이지 → 비전 건너뜀
        extractor.extract(new UploadedDocument("blank.pdf", "application/pdf", blankPdf()));
        assertThat(calls).hasValue(0);

        // 이미지 있는 페이지 → 비전 실행 + 설명 병합
        ExtractedDocument document = extractor.extract(
                new UploadedDocument("with-image.pdf", "application/pdf", pdfWithImage()));
        assertThat(calls).hasValue(1);
        assertThat(document.sourceUnits().get(0).text())
                .contains("[시각 자료 설명]")
                .contains("이미지 안의 도식 설명");
    }

    private OllamaVisionProperties visionProperties(String mode) {
        return new OllamaVisionProperties(
                true, "qwen3-vl:4b", "http://localhost:11434",
                5, 180, 72, mode, true, false, "Analyze:");
    }

    private byte[] pdfWithImage() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDImageXObject image = LosslessFactory.createFromImage(
                    document, new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB));
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(image, 20, 20, 40, 40);
            }
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] blankPdf() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }
}
