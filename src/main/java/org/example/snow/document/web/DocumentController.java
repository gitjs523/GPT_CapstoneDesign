package org.example.snow.document.web;

import lombok.RequiredArgsConstructor;
import org.example.snow.document.application.DocumentIngestionService;
import org.example.snow.document.web.dto.DocumentProcessResponse;
import org.example.snow.document.web.dto.DocumentUploadRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentIngestionService documentIngestionService;

    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentProcessResponse> extract(@ModelAttribute DocumentUploadRequest request) {
        return ResponseEntity.ok(DocumentProcessResponse.from(documentIngestionService.ingest(request.toCommand())));
    }
}
