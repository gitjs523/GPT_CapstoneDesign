package org.example.snow.document.web;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void extractsTextFromPdf() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "lecture.pdf",
                "application/pdf",
                createPdf("RAG overview")
        );

        mockMvc.perform(multipart("/api/documents/extract")
                        .file(file)
                        .param("chunkStrategy", "AUTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename").value("lecture.pdf"))
                .andExpect(jsonPath("$.appliedChunkStrategy").value("PAGE"))
                .andExpect(jsonPath("$.chunkCount").value(1))
                .andExpect(jsonPath("$.chunks[0].heading").value("Page 1"))
                .andExpect(jsonPath("$.chunks[0].text", containsString("RAG overview")));
    }

    @Test
    void rejectsUnsupportedFileType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "plain text".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/documents/extract")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DOC_002"))
                .andExpect(jsonPath("$.message").value("지원하지 않는 파일 형식입니다. PDF, PPT, PPTX만 업로드할 수 있습니다."));
    }

    private byte[] createPdf(String text) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(72, 720);
                contentStream.showText(text);
                contentStream.endText();
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }
}
