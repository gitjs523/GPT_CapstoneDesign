package org.example.snow.document.application.port;

import org.example.snow.document.application.UploadedDocument;
import org.example.snow.document.domain.ExtractedDocument;

public interface TextExtractor {

    boolean supports(UploadedDocument file);

    ExtractedDocument extract(UploadedDocument file);
}
