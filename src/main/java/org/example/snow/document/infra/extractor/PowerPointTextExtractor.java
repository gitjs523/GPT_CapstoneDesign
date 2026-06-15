package org.example.snow.document.infra.extractor;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.sl.extractor.SlideShowExtractor;
import org.apache.poi.sl.usermodel.PictureShape;
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
    private static final String IMAGE_TEXT_HEADING = "[이미지 내 텍스트]";
    private static final String VISUAL_ANALYSIS_HEADING = "[시각 자료 설명]";

    private final OcrTextExtractor ocrTextExtractor;
    private final OllamaOcrProperties ocrProperties;
    private final VisualContentAnalyzer visualContentAnalyzer;
    private final OllamaVisionProperties visionProperties;

    public PowerPointTextExtractor() {
        this(image -> "", OllamaOcrProperties.disabled(), image -> "", OllamaVisionProperties.disabled());
    }

    @Autowired
    public PowerPointTextExtractor(
            OllamaOcrClient ocrTextExtractor,
            OllamaOcrProperties ocrProperties,
            OllamaVisionClient visualContentAnalyzer,
            OllamaVisionProperties visionProperties
    ) {
        this((OcrTextExtractor) ocrTextExtractor, ocrProperties, visualContentAnalyzer, visionProperties);
    }

    PowerPointTextExtractor(OcrTextExtractor ocrTextExtractor, OllamaOcrProperties ocrProperties) {
        this(ocrTextExtractor, ocrProperties, image -> "", OllamaVisionProperties.disabled());
    }

    PowerPointTextExtractor(
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
                BufferedImage slideImage = null;
                if (shouldExtractSlideImageText()) {
                    slideImage = renderSlideIfNecessary(slideImage, slideShow, slide);
                    slideText = mergeBlock(slideText, IMAGE_TEXT_HEADING, extractSlideImageText(slideImage, slideNumber));
                }
                if (shouldAnalyzeSlideVisual(slide)) {
                    slideImage = renderSlideIfNecessary(slideImage, slideShow, slide);
                    slideText = mergeBlock(slideText, VISUAL_ANALYSIS_HEADING, analyzeSlideVisual(slideImage, slideNumber));
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

    private boolean shouldExtractSlideImageText() {
        return ocrProperties.enabled() && ocrProperties.powerpointEnabled();
    }

    private <S extends Shape<S, P>, P extends TextParagraph<S, P, ? extends TextRun>> boolean shouldAnalyzeSlideVisual(
            Slide<S, P> slide
    ) {
        if (!visionProperties.enabled() || !visionProperties.powerpointEnabled()) {
            return false;
        }
        if (visionProperties.alwaysAnalyze()) {
            return true;
        }
        return visionProperties.imageOnly() && slideHasImage(slide);
    }

    /** 슬라이드에 그림(PictureShape)이 있는지 검사한다. image 모드 전용 트리거. */
    private <S extends Shape<S, P>, P extends TextParagraph<S, P, ? extends TextRun>> boolean slideHasImage(
            Slide<S, P> slide
    ) {
        for (Shape<S, P> shape : slide.getShapes()) {
            if (shape instanceof PictureShape) {
                return true;
            }
        }
        return false;
    }

    private String extractSlideImageText(BufferedImage image, int slideNumber) {
        if (image == null) {
            return "";
        }
        try {
            return ocrTextExtractor.extractText(image);
        } catch (RuntimeException exception) {
            log.warn("OCR failed for PowerPoint slide {}. Using slide text extraction result.", slideNumber, exception);
            return "";
        }
    }

    private String analyzeSlideVisual(BufferedImage image, int slideNumber) {
        if (image == null) {
            return "";
        }
        try {
            return visualContentAnalyzer.analyze(image);
        } catch (RuntimeException exception) {
            log.warn("Visual analysis failed for PowerPoint slide {}. Using slide text extraction result.",
                    slideNumber, exception);
            return "";
        }
    }

    private <S extends Shape<S, P>, P extends TextParagraph<S, P, ? extends TextRun>> BufferedImage renderSlideIfNecessary(
            BufferedImage currentImage,
            SlideShow<S, P> slideShow,
            Slide<S, P> slide
    ) {
        return currentImage != null ? currentImage : renderSlide(slideShow, slide);
    }

    private <S extends Shape<S, P>, P extends TextParagraph<S, P, ? extends TextRun>> BufferedImage renderSlide(
            SlideShow<S, P> slideShow,
            Slide<S, P> slide
    ) {
        Dimension pageSize = slideShow.getPageSize();
        double scale = Math.max(72, Math.max(ocrProperties.renderDpi(), visionProperties.renderDpi())) / 72.0;
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

    private String mergeBlock(String baseText, String heading, String blockText) {
        if (!StringUtils.hasText(blockText)) {
            return baseText;
        }
        String trimmedBlock = blockText.trim();
        if (compact(baseText).contains(compact(trimmedBlock))) {
            return baseText;
        }
        if (!StringUtils.hasText(baseText)) {
            return heading + "\n" + trimmedBlock;
        }
        return baseText.trim() + "\n\n" + heading + "\n" + trimmedBlock;
    }

    private String compact(String text) {
        return StringUtils.hasText(text) ? text.replaceAll("\\s+", "") : "";
    }
}
