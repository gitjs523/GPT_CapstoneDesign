package org.example.snow.document.infra.extractor;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.example.snow.document.application.UploadedDocument;
import org.example.snow.document.domain.ExtractedDocument;
import org.example.snow.document.domain.SourceUnitType;
import org.junit.jupiter.api.Test;

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

    private byte[] createPptx(String text) throws IOException {
        try (XMLSlideShow slideShow = new XMLSlideShow(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            slideShow.createSlide().createTextBox().setText(text);
            slideShow.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
