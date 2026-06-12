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

    private static final String VISUAL_ANALYSIS_HEADING = "[시각 자료 설명]";

    private final OcrTextExtractor ocrTextExtractor;
    private final OllamaOcrProperties ocrProperties;
    private final VisualContentAnalyzer visualContentAnalyzer;
    private final OllamaVisionProperties visionProperties;

    public PdfTextExtractor() {
        this(image -> "", OllamaOcrProperties.disabled(), image -> "", OllamaVisionProperties.disabled());
    }

    @Autowired
    public PdfTextExtractor(
            OllamaOcrClient ocrTextExtractor,
            OllamaOcrProperties ocrProperties,
            OllamaVisionClient visualContentAnalyzer,
            OllamaVisionProperties visionProperties
    ) {
        this((OcrTextExtractor) ocrTextExtractor, ocrProperties, visualContentAnalyzer, visionProperties);
    }

    PdfTextExtractor(OcrTextExtractor ocrTextExtractor, OllamaOcrProperties ocrProperties) {
        this(ocrTextExtractor, ocrProperties, image -> "", OllamaVisionProperties.disabled());
    }

    PdfTextExtractor(
            OcrTextExtractor ocrTextExtractor,
            OllamaOcrProperties ocrProperties,
            VisualContentAnalyzer visualContentAnalyzer,
            OllamaVisionProperties visionProperties
    ) {
        this.ocrTextExtractor = ocrTextExtractor;
        this.ocrProperties = ocrProperties;
        this.visualContentAnalyzer = visualContentAnalyzer;
        this.visionProperties = visionProperties;
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
            PDFRenderer renderer = needsRenderer() ? new PDFRenderer(document) : null;
            List<ExtractedSourceUnit> sourceUnits = new ArrayList<>();

            for (int pageNumber = 1; pageNumber <= document.getNumberOfPages(); pageNumber++) {
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);
                String pageText = stripper.getText(document);

                BufferedImage pageImage = null;
                if (needsOcr(pageText)) {
                    pageImage = renderPageIfNecessary(renderer, pageImage, pageNumber);
                    pageText = extractWithOcr(pageImage, pageNumber, pageText);
                }
                if (needsVisualAnalysis(pageText)) {
                    pageImage = renderPageIfNecessary(renderer, pageImage, pageNumber);
                    pageText = mergeVisualAnalysis(pageText, analyzeVisualContent(pageImage, pageNumber));
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

    private boolean needsVisualAnalysis(String text) {
        if (!visionProperties.enabled() || !visionProperties.pdfEnabled()) {
            return false;
        }
        return visionProperties.alwaysAnalyze()
                || (visionProperties.fallbackOnly() && compactLength(text) < Math.max(0, ocrProperties.minTextChars()));
    }

    private boolean needsRenderer() {
        return ocrProperties.enabled() || (visionProperties.enabled() && visionProperties.pdfEnabled());
    }

    private BufferedImage renderPageIfNecessary(
            PDFRenderer renderer,
            BufferedImage currentImage,
            int pageNumber
    ) throws IOException {
        if (currentImage != null || renderer == null) {
            return currentImage;
        }
        return renderer.renderImageWithDPI(
                pageNumber - 1,
                Math.max(72, Math.max(ocrProperties.renderDpi(), visionProperties.renderDpi())),
                ImageType.RGB
        );
    }

    private String extractWithOcr(BufferedImage image, int pageNumber, String fallbackText) {
        if (image == null) {
            return fallbackText;
        }
        try {
            String ocrText = ocrTextExtractor.extractText(image);
            return StringUtils.hasText(ocrText) ? ocrText : fallbackText;
        } catch (RuntimeException exception) {
            log.warn("OCR fallback failed for PDF page {}. Using PDF text extraction result.", pageNumber, exception);
            return fallbackText;
        }
    }

    private String analyzeVisualContent(BufferedImage image, int pageNumber) {
        if (image == null) {
            return "";
        }
        try {
            return visualContentAnalyzer.analyze(image);
        } catch (RuntimeException exception) {
            log.warn("Visual analysis failed for PDF page {}. Using text extraction result.", pageNumber, exception);
            return "";
        }
    }

    private String mergeVisualAnalysis(String text, String visualAnalysis) {
        if (!StringUtils.hasText(visualAnalysis)) {
            return text;
        }
        String trimmedAnalysis = visualAnalysis.trim();
        if (compact(text).contains(compact(trimmedAnalysis))) {
            return text;
        }
        if (!StringUtils.hasText(text)) {
            return VISUAL_ANALYSIS_HEADING + "\n" + trimmedAnalysis;
        }
        return text.trim() + "\n\n" + VISUAL_ANALYSIS_HEADING + "\n" + trimmedAnalysis;
    }

    private int compactLength(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        return text.replaceAll("\\s+", "").length();
    }

    private String compact(String text) {
        return StringUtils.hasText(text) ? text.replaceAll("\\s+", "") : "";
    }
}
