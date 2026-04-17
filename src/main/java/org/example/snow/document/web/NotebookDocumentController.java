package org.example.snow.document.web;

import lombok.RequiredArgsConstructor;
import org.example.snow.auth.security.AuthenticatedUserPrincipal;
import org.example.snow.document.application.DocumentService;
import org.example.snow.document.application.DocumentUploadCommand;
import org.example.snow.document.application.UploadedDocument;
import org.example.snow.document.web.dto.DocumentResponse;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notebooks/{notebookId}/documents")
public class NotebookDocumentController {

    private final DocumentService documentService;

    @GetMapping
    public ResponseEntity<List<DocumentResponse>> getDocuments(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @PathVariable Long notebookId) {
        List<DocumentResponse> documents = documentService.getDocuments(principal.userId(), notebookId)
                .stream()
                .map(DocumentResponse::from)
                .toList();
        return ResponseEntity.ok(documents);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> uploadDocument(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @PathVariable Long notebookId,
            @RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_REQUIRED);
        }
        UploadedDocument uploaded = new UploadedDocument(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes()
        );
        DocumentResponse response = DocumentResponse.from(
                documentService.createDocument(principal.userId(), notebookId, new DocumentUploadCommand(uploaded, null))
        );
        return ResponseEntity.created(
                URI.create("/api/notebooks/" + notebookId + "/documents/" + response.documentId())
        ).body(response);
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentResponse> getDocument(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @PathVariable Long notebookId,
            @PathVariable Long documentId) {
        return ResponseEntity.ok(DocumentResponse.from(
                documentService.getDocument(principal.userId(), notebookId, documentId)
        ));
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @PathVariable Long notebookId,
            @PathVariable Long documentId) {
        documentService.deleteDocument(principal.userId(), notebookId, documentId);
        return ResponseEntity.noContent().build();
    }
}
