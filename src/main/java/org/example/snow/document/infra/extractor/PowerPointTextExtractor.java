package org.example.snow.document.infra.extractor;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.sl.extractor.SlideShowExtractor;
import org.apache.poi.sl.usermodel.Shape;
import org.apache.poi.sl.usermodel.Slide;
import org.apache.poi.sl.usermodel.SlideShow;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.sl.usermodel.TextRun;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.example.snow.document.application.UploadedDocument;
import org.example.snow.document.application.port.TextExtractor;
import org.example.snow.document.domain.ExtractedDocument;
import org.example.snow.document.domain.ExtractedSourceUnit;
import org.example.snow.document.domain.SourceUnitType;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class PowerPointTextExtractor implements TextExtractor {

    static final String PPT_CONTENT_TYPE = "application/vnd.ms-powerpoint";
    static final String PPTX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.presentationml.presentation";

    private final OcrTextExtractor ocrTextExtractor;
    private final OllamaOcrProperties ocrProperties;

    public PowerPointTextExtractor() {
        this(image -> "", OllamaOcrProperties.disabled());
    }

    @Autowired
    public PowerPointTextExtractor(OllamaOcrClient ocrTextExtractor, OllamaOcrProperties ocrProperties) {
        this((OcrTextExtractor) ocrTextExtractor, ocrProperties);
    }

    PowerPointTextExtractor(OcrTextExtractor ocrTextExtractor, OllamaOcrProperties ocrProperties) {
        this.ocrTextExtractor = ocrTextExtractor;
        this.ocrProperties = ocrProperties;
    }

    @Override
    public boolean supports(UploadedDocument file) {
        return isPpt(file) || isPptx(file);
    }

    @Override
    public ExtractedDocument extract(UploadedDocument file) {
        try (InputStream inputStream = new ByteArrayInputStream(file.content())) {
            if (isPptx(file)) {
                return extractSlideShow(new XMLSlideShow(inputStream), file);
            }
            return extractSlideShow(new HSLFSlideShow(inputStream), file);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.POWERPOINT_TEXT_EXTRACTION_FAILED, exception);
        }
    }

    private boolean isPpt(UploadedDocument file) {
        return ExtractorSupport.hasExtension(file, "ppt")
                || ExtractorSupport.hasContentType(file, PPT_CONTENT_TYPE);
    }

    private boolean isPptx(UploadedDocument file) {
        return ExtractorSupport.hasExtension(file, "pptx")
                || ExtractorSupport.hasContentType(file, PPTX_CONTENT_TYPE);
    }

    private <S extends Shape<S, P>, P extends TextParagraph<S, P, ? extends TextRun>> ExtractedDocument extractSlideShow(
            SlideShow<S, P> slideShow,
            UploadedDocument file
    ) throws IOException {
        try (slideShow; SlideShowExtractor<S, P> extractor = new SlideShowExtractor<>(slideShow)) {
            List<ExtractedSourceUnit> sourceUnits = new ArrayList<>();
            int slideNumber = 1;

            for (Slide<S, P> slide : slideShow.getSlides()) {
                String slideText = extractor.getText(slide);
                if (shouldAnalyzeSlideImage()) {
                    slideText = mergeOcrText(slideText, extractSlideImageText(slideShow, slide, slideNumber));
                }
                sourceUnits.add(ExtractorSupport.slideSourceUnit(
                        slideNumber,
                        slide.getTitle(),
                        slideText
                ));
                slideNumber++;
            }

            return new ExtractedDocument(
                    ExtractorSupport.resolveFilename(file),
                    ExtractorSupport.resolveContentType(file),
                    SourceUnitType.SLIDE,
                    sourceUnits
            );
        }
    }

    private boolean shouldAnalyzeSlideImage() {
        return ocrProperties.enabled() && ocrProperties.powerpointEnabled();
    }

    private <S extends Shape<S, P>, P extends TextParagraph<S, P, ? extends TextRun>> String extractSlideImageText(
            SlideShow<S, P> slideShow,
            Slide<S, P> slide,
            int slideNumber
    ) {
        try {
            BufferedImage image = renderSlide(slideShow, slide);
            return ocrTextExtractor.extractText(image);
        } catch (RuntimeException exception) {
            log.warn("OCR failed for PowerPoint slide {}. Using slide text extraction result.", slideNumber, exception);
            return "";
        }
    }

    private <S extends Shape<S, P>, P extends TextParagraph<S, P, ? extends TextRun>> BufferedImage renderSlide(
            SlideShow<S, P> slideShow,
            Slide<S, P> slide
    ) {
        Dimension pageSize = slideShow.getPageSize();
        double scale = Math.max(72, ocrProperties.renderDpi()) / 72.0;
        int width = Math.max(1, (int) Math.ceil(pageSize.getWidth() * scale));
        int height = Math.max(1, (int) Math.ceil(pageSize.getHeight() * scale));

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.scale(scale, scale);
            slide.draw(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private String mergeOcrText(String slideText, String ocrText) {
        if (!StringUtils.hasText(ocrText)) {
            return slideText;
        }
        if (!StringUtils.hasText(slideText)) {
            return ocrText.trim();
        }

        String slideCompact = compact(slideText);
        String ocrCompact = compact(ocrText);
        if (slideCompact.contains(ocrCompact)) {
            return slideText;
        }
        if (ocrCompact.contains(slideCompact)) {
            return ocrText.trim();
        }
        return slideText.trim() + "\n\n" + ocrText.trim();
    }

    private String compact(String text) {
        return StringUtils.hasText(text) ? text.replaceAll("\\s+", "") : "";
    }
}
