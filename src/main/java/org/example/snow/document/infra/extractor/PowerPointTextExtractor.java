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
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class PowerPointTextExtractor implements TextExtractor {

    private static final String PPT_CONTENT_TYPE = "application/vnd.ms-powerpoint";
    private static final String PPTX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.presentationml.presentation";

    @Override
    public boolean supports(UploadedDocument file) {
        String filename = file.originalFilename();
        String contentType = file.contentType();

        return hasExtension(filename, "ppt")
                || hasExtension(filename, "pptx")
                || PPT_CONTENT_TYPE.equalsIgnoreCase(contentType)
                || PPTX_CONTENT_TYPE.equalsIgnoreCase(contentType);
    }

    @Override
    public ExtractedDocument extract(UploadedDocument file) {
        try (InputStream inputStream = new ByteArrayInputStream(file.content())) {
            if (hasExtension(file.originalFilename(), "pptx")) {
                return extractSlideShow(new XMLSlideShow(inputStream), file);
            }
            return extractSlideShow(new HSLFSlideShow(inputStream), file);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.POWERPOINT_TEXT_EXTRACTION_FAILED, exception);
        }
    }

    private <S extends Shape<S, P>, P extends TextParagraph<S, P, ? extends TextRun>> ExtractedDocument extractSlideShow(
            SlideShow<S, P> slideShow,
            UploadedDocument file
    ) throws IOException {
        try (slideShow; SlideShowExtractor<S, P> extractor = new SlideShowExtractor<>(slideShow)) {
            List<SourceUnit> sourceUnits = new ArrayList<>();
            int slideNumber = 1;

            for (Slide<S, P> slide : slideShow.getSlides()) {
                String heading = StringUtils.hasText(slide.getTitle()) ? slide.getTitle() : "Slide " + slideNumber;
                String text = extractor.getText(slide);
                sourceUnits.add(new SourceUnit(slideNumber, heading, text));
                slideNumber++;
            }

            return new ExtractedDocument(
                    resolveFilename(file),
                    resolveContentType(file),
                    SourceUnitType.SLIDE,
                    sourceUnits
            );
        }
    }

    private boolean hasExtension(String filename, String extension) {
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith("." + extension);
    }

    private String resolveFilename(UploadedDocument file) {
        return file.originalFilename() == null ? "uploaded-file" : file.originalFilename();
    }

    private String resolveContentType(UploadedDocument file) {
        return file.contentType() == null ? "application/octet-stream" : file.contentType();
    }
}
