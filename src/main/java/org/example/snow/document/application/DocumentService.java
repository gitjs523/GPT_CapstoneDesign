package org.example.snow.document.application;

import lombok.RequiredArgsConstructor;
import org.example.snow.document.domain.Document;
import org.example.snow.document.infra.DocumentRepository;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.example.snow.notebook.domain.Notebook;
import org.example.snow.notebook.infra.NotebookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final NotebookRepository notebookRepository;
    private final DocumentAnalysisService documentAnalysisService;

    @Transactional(readOnly = true)
    public List<Document> getDocuments(Long userId, Long notebookId) {
        Notebook notebook = getNotebookWithOwnershipCheck(userId, notebookId);
        return documentRepository.findAllByNotebook_NotebookIdOrderByUploadedAtAsc(notebook.getNotebookId());
    }

    @Transactional
    public Document createDocument(Long userId, Long notebookId, DocumentUploadCommand command) {
        Notebook notebook = getNotebookWithOwnershipCheck(userId, notebookId);
        UploadedDocument file = command.file();
        String fileType = resolveFileType(file.contentType(), file.originalFilename());
        Document document = Document.create(
                notebook,
                file.originalFilename(),
                file.originalFilename(),
                fileType,
                (long) file.content().length
        );
        Document saved = documentRepository.save(document);
        documentAnalysisService.analyzeAsync(saved.getDocumentId(), command);
        return saved;
    }

    @Transactional(readOnly = true)
    public Document getDocument(Long userId, Long notebookId, Long documentId) {
        getNotebookWithOwnershipCheck(userId, notebookId);
        return getDocumentWithNotebookCheck(documentId, notebookId);
    }

    @Transactional
    public void deleteDocument(Long userId, Long notebookId, Long documentId) {
        getNotebookWithOwnershipCheck(userId, notebookId);
        Document document = getDocumentWithNotebookCheck(documentId, notebookId);
        documentRepository.delete(document);
    }

    private Notebook getNotebookWithOwnershipCheck(Long userId, Long notebookId) {
        Notebook notebook = notebookRepository.findById(notebookId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTEBOOK_NOT_FOUND));
        if (!notebook.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOTEBOOK_ACCESS_DENIED);
        }
        return notebook;
    }

    private Document getDocumentWithNotebookCheck(Long documentId, Long notebookId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
        if (!document.getNotebook().getNotebookId().equals(notebookId)) {
            throw new BusinessException(ErrorCode.DOCUMENT_ACCESS_DENIED);
        }
        return document;
    }

    private String resolveFileType(String contentType, String filename) {
        if (contentType != null) {
            if (contentType.equals("application/pdf")) return "PDF";
            if (contentType.equals("application/vnd.ms-powerpoint")) return "PPT";
            if (contentType.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation")) return "PPTX";
        }
        if (filename != null) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".pdf")) return "PDF";
            if (lower.endsWith(".ppt")) return "PPT";
            if (lower.endsWith(".pptx")) return "PPTX";
        }
        return "UNKNOWN";
    }
}
