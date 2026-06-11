package org.example.snow.document.infra.extractor;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.snow.document.application.UploadedDocument;
import org.example.snow.document.application.port.TextExtractor;
import org.example.snow.document.domain.ExtractedDocument;
import org.example.snow.document.domain.ExtractedSourceUnit;
import org.example.snow.document.domain.SourceUnitType;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class PdfTextExtractor implements TextExtractor {

    private final OcrTextExtractor ocrTextExtractor;
    private final OllamaOcrProperties ocrProperties;

    public PdfTextExtractor() {
        this(image -> "", OllamaOcrProperties.disabled());
    }

    @Autowired
    public PdfTextExtractor(OllamaOcrClient ocrTextExtractor, OllamaOcrProperties ocrProperties) {
        this((OcrTextExtractor) ocrTextExtractor, ocrProperties);
    }

    PdfTextExtractor(OcrTextExtractor ocrTextExtractor, OllamaOcrProperties ocrProperties) {
        this.ocrTextExtractor = ocrTextExtractor;
        this.ocrProperties = ocrProperties;
    }

    @Override
    public boolean supports(UploadedDocument file) {
        return ExtractorSupport.hasExtension(file, "pdf")
                || ExtractorSupport.hasContentType(file, MediaType.APPLICATION_PDF_VALUE);
    }

    @Override
    public ExtractedDocument extract(UploadedDocument file) {
        try (PDDocument document = Loader.loadPDF(file.content())) {
            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = ocrProperties.enabled() ? new PDFRenderer(document) : null;
            List<ExtractedSourceUnit> sourceUnits = new ArrayList<>();

            for (int pageNumber = 1; pageNumber <= document.getNumberOfPages(); pageNumber++) {
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);
                String pageText = stripper.getText(document);
                if (needsOcr(pageText)) {
                    pageText = extractWithOcr(renderer, pageNumber, pageText);
                }
                sourceUnits.add(ExtractorSupport.pageSourceUnit(pageNumber, pageText));
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

    private boolean needsOcr(String text) {
        if (!ocrProperties.enabled()) {
            return false;
        }
        return compactLength(text) < Math.max(0, ocrProperties.minTextChars());
    }

    private String extractWithOcr(PDFRenderer renderer, int pageNumber, String fallbackText) throws IOException {
        if (renderer == null) {
            return fallbackText;
        }
        BufferedImage image = renderer.renderImageWithDPI(
                pageNumber - 1,
                Math.max(72, ocrProperties.renderDpi()),
                ImageType.RGB
        );
        try {
            String ocrText = ocrTextExtractor.extractText(image);
            return StringUtils.hasText(ocrText) ? ocrText : fallbackText;
        } catch (RuntimeException exception) {
            log.warn("OCR fallback failed for PDF page {}. Using PDF text extraction result.", pageNumber, exception);
            return fallbackText;
        }
    }

    private int compactLength(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        return text.replaceAll("\\s+", "").length();
    }
}
