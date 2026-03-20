package org.example.snow.document.infra.extractor;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.snow.document.application.UploadedDocument;
import org.example.snow.document.application.port.TextExtractor;
import org.example.snow.document.domain.ExtractedDocument;
import org.example.snow.document.domain.ExtractedSection;
import org.example.snow.document.domain.SourceUnitType;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class PdfTextExtractor implements TextExtractor {

    @Override
    public boolean supports(UploadedDocument file) {
        String filename = file.originalFilename();
        String contentType = file.contentType();

        return hasExtension(filename, "pdf") || MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(contentType);
    }

    @Override
    public ExtractedDocument extract(UploadedDocument file) {
        try (PDDocument document = Loader.loadPDF(file.content())) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<ExtractedSection> sections = new ArrayList<>();

            for (int pageNumber = 1; pageNumber <= document.getNumberOfPages(); pageNumber++) {
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);
                String text = stripper.getText(document);
                sections.add(new ExtractedSection(pageNumber, "Page " + pageNumber, text));
            }

            return new ExtractedDocument(
                    resolveFilename(file),
                    resolveContentType(file),
                    SourceUnitType.PAGE,
                    sections
            );
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.PDF_TEXT_EXTRACTION_FAILED, exception);
        }
    }

    private boolean hasExtension(String filename, String extension) {
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith("." + extension);
    }

    private String resolveFilename(UploadedDocument file) {
        return file.originalFilename() == null ? "uploaded-file" : file.originalFilename();
    }

    private String resolveContentType(UploadedDocument file) {
        return file.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.contentType();
    }
}
