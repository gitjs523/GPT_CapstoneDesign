package org.example.snow.document.infra.extractor;

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
import org.example.snow.document.domain.SourceUnit;
import org.example.snow.document.domain.SourceUnitType;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
public class PowerPointTextExtractor implements TextExtractor {

    static final String PPT_CONTENT_TYPE = "application/vnd.ms-powerpoint";
    static final String PPTX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.presentationml.presentation";

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
            List<SourceUnit> sourceUnits = new ArrayList<>();
            int slideNumber = 1;

            for (Slide<S, P> slide : slideShow.getSlides()) {
                sourceUnits.add(ExtractorSupport.slideSourceUnit(
                        slideNumber,
                        slide.getTitle(),
                        extractor.getText(slide)
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
}
