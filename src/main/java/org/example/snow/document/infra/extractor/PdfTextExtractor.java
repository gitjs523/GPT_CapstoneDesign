package org.example.snow.document.infra.extractor;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.snow.document.application.UploadedDocument;
import org.example.snow.document.application.port.TextExtractor;
import org.example.snow.document.domain.ExtractedDocument;
import org.example.snow.document.domain.SourceUnit;
import org.example.snow.document.domain.SourceUnitType;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class PdfTextExtractor implements TextExtractor {

    @Override
    public boolean supports(UploadedDocument file) {
        return ExtractorSupport.hasExtension(file, "pdf")
                || ExtractorSupport.hasContentType(file, MediaType.APPLICATION_PDF_VALUE);
    }

    @Override
    public ExtractedDocument extract(UploadedDocument file) {
        try (PDDocument document = Loader.loadPDF(file.content())) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<SourceUnit> sourceUnits = new ArrayList<>();

            for (int pageNumber = 1; pageNumber <= document.getNumberOfPages(); pageNumber++) {
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);
                sourceUnits.add(ExtractorSupport.pageSourceUnit(pageNumber, stripper.getText(document)));
            }

            return new ExtractedDocument(
                    ExtractorSupport.resolveFilename(file),
                    ExtractorSupport.resolveContentType(file),
                    SourceUnitType.PAGE,
                    sourceUnits
            );
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.PDF_TEXT_EXTRACTION_FAILED, exception);
        }
    }
}
