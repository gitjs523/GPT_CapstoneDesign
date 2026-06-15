package org.example.snow.document.infra.extractor;

import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.example.snow.document.application.UploadedDocument;
import org.example.snow.document.domain.ExtractedDocument;
import org.example.snow.document.domain.SourceUnitType;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PowerPointTextExtractorTest {

    private final PowerPointTextExtractor extractor = new PowerPointTextExtractor();

    @Test
    void extractsPptxWhenOnlyContentTypeIndicatesTheFormat() throws Exception {
        UploadedDocument file = new UploadedDocument(
                "lecture",
                PowerPointTextExtractor.PPTX_CONTENT_TYPE,
                createPptx("RAG overview")
        );

        assertThat(extractor.supports(file)).isTrue();

        ExtractedDocument extractedDocument = extractor.extract(file);

        assertThat(extractedDocument.originalFilename()).isEqualTo("lecture");
        assertThat(extractedDocument.contentType()).isEqualTo(PowerPointTextExtractor.PPTX_CONTENT_TYPE);
        assertThat(extractedDocument.sourceType()).isEqualTo(SourceUnitType.SLIDE);
        assertThat(extractedDocument.sourceUnits()).hasSize(1);
        assertThat(extractedDocument.sourceUnits().get(0).heading()).isEqualTo("Slide 1");
        assertThat(extractedDocument.sourceUnits().get(0).text()).contains("RAG overview");
    }

    @Test
    void appendsSlideImageOcrTextWhenPowerPointOcrIsEnabled() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        PowerPointTextExtractor ocrExtractor = new PowerPointTextExtractor(
                image -> {
                    calls.incrementAndGet();
                    return "RAG overview\nImage-only keyword";
                },
                new OllamaOcrProperties(
                        true,
                        "glm-ocr:latest",
                        "http://localhost:11434",
                        5,
                        180,
                        30,
                        72,
                        true,
                        "Text Recognition:"
                )
        );
        UploadedDocument file = new UploadedDocument(
                "lecture.pptx",
                PowerPointTextExtractor.PPTX_CONTENT_TYPE,
                createPptx("RAG overview")
        );

        ExtractedDocument extractedDocument = ocrExtractor.extract(file);

        assertThat(calls).hasValue(1);
        assertThat(extractedDocument.sourceUnits().get(0).text()).contains("Image-only keyword");
    }

    @Test
    void appendsVisualAnalysisWhenPowerPointVisionIsEnabled() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        PowerPointTextExtractor visionExtractor = new PowerPointTextExtractor(
                image -> "",
                OllamaOcrProperties.disabled(),
                image -> {
                    calls.incrementAndGet();
                    return "이 슬라이드는 RAG 파이프라인에서 질문, 검색기, 문서, LLM, 답변의 관계를 설명한다.";
                },
                new OllamaVisionProperties(
                        true,
                        "qwen3-vl:4b",
                        "http://localhost:11434",
                        5,
                        180,
                        72,
                        "always",
                        false,
                        true,
                        "Analyze:"
                )
        );
        UploadedDocument file = new UploadedDocument(
                "lecture.pptx",
                PowerPointTextExtractor.PPTX_CONTENT_TYPE,
                createPptx("RAG overview")
        );

        ExtractedDocument extractedDocument = visionExtractor.extract(file);

        assertThat(calls).hasValue(1);
        assertThat(extractedDocument.sourceUnits().get(0).text())
                .contains("[시각 자료 설명]")
                .contains("RAG 파이프라인");
    }

    @Test
    void imageMode_runsSlideVisionOnlyWhenSlideHasPicture() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        PowerPointTextExtractor extractor = new PowerPointTextExtractor(
                image -> "",
                OllamaOcrProperties.disabled(),
                image -> {
                    calls.incrementAndGet();
                    return "슬라이드 그림 설명";
                },
                visionProperties("image")
        );

        // 그림 없는 슬라이드(텍스트만) → 건너뜀
        extractor.extract(new UploadedDocument(
                "text.pptx", PowerPointTextExtractor.PPTX_CONTENT_TYPE, createPptx("RAG overview")));
        assertThat(calls).hasValue(0);

        // 그림 있는 슬라이드 → 비전 실행
        ExtractedDocument document = extractor.extract(new UploadedDocument(
                "image.pptx", PowerPointTextExtractor.PPTX_CONTENT_TYPE, pptxWithPicture()));
        assertThat(calls).hasValue(1);
        assertThat(document.sourceUnits().get(0).text()).contains("[시각 자료 설명]");
    }

    private OllamaVisionProperties visionProperties(String mode) {
        return new OllamaVisionProperties(
                true, "qwen3-vl:4b", "http://localhost:11434",
                5, 180, 72, mode, false, true, "Analyze:");
    }

    private byte[] pptxWithPicture() throws Exception {
        try (XMLSlideShow slideShow = new XMLSlideShow(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            var slide = slideShow.createSlide();
            slide.createTextBox().setText("RAG overview");
            byte[] png;
            try (ByteArrayOutputStream pngStream = new ByteArrayOutputStream()) {
                ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", pngStream);
                png = pngStream.toByteArray();
            }
            slide.createPicture(slideShow.addPicture(png, PictureData.PictureType.PNG));
            slideShow.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] createPptx(String text) throws IOException {
        try (XMLSlideShow slideShow = new XMLSlideShow(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            slideShow.createSlide().createTextBox().setText(text);
            slideShow.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
