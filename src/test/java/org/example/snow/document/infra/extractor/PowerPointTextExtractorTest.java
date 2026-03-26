package org.example.snow.document.infra.extractor;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.example.snow.document.application.UploadedDocument;
import org.example.snow.document.domain.ExtractedDocument;
import org.example.snow.document.domain.SourceUnitType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

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

    private byte[] createPptx(String text) throws IOException {
        try (XMLSlideShow slideShow = new XMLSlideShow(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            slideShow.createSlide().createTextBox().setText(text);
            slideShow.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
