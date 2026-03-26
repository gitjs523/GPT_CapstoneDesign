package org.example.snow.document.infra.extractor;

import org.example.snow.document.application.UploadedDocument;
import org.example.snow.document.domain.SourceUnit;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

import java.util.Locale;

final class ExtractorSupport {

    private static final String DEFAULT_FILENAME = "uploaded-file";

    private ExtractorSupport() {
    }

    static boolean hasExtension(UploadedDocument file, String extension) {
        String filename = file.originalFilename();
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith("." + extension);
    }

    static boolean hasContentType(UploadedDocument file, String contentType) {
        return StringUtils.hasText(file.contentType()) && contentType.equalsIgnoreCase(file.contentType());
    }

    static String resolveFilename(UploadedDocument file) {
        return StringUtils.hasText(file.originalFilename()) ? file.originalFilename() : DEFAULT_FILENAME;
    }

    static String resolveContentType(UploadedDocument file) {
        return StringUtils.hasText(file.contentType()) ? file.contentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    static SourceUnit pageSourceUnit(int pageNumber, String text) {
        return new SourceUnit(pageNumber, "Page " + pageNumber, text);
    }

    static SourceUnit slideSourceUnit(int slideNumber, String heading, String text) {
        String resolvedHeading = StringUtils.hasText(heading) ? heading : "Slide " + slideNumber;
        return new SourceUnit(slideNumber, resolvedHeading, text);
    }
}
