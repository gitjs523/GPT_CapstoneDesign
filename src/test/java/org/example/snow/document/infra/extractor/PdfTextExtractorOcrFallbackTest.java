package org.example.snow.document.infra.extractor;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.example.snow.document.application.UploadedDocument;
import org.example.snow.document.domain.ExtractedDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

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

    private byte[] blankPdf() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }
}
