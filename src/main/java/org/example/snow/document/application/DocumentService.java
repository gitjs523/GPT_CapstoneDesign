package org.example.snow.document.application;

import lombok.RequiredArgsConstructor;
import org.example.snow.ai.infra.GeneratedQuizRepository;
import org.example.snow.ai.infra.GenerationJobRepository;
import org.example.snow.ai.infra.NotebookQaHistoryRepository;
import org.example.snow.ai.infra.QuizQaHistoryRepository;
import org.example.snow.document.domain.AnalysisStatus;
import org.example.snow.document.domain.Document;
import org.example.snow.document.domain.Section;
import org.example.snow.document.infra.ChunkRepository;
import org.example.snow.document.infra.DocumentRepository;
import org.example.snow.document.infra.SectionRepository;
import org.example.snow.document.application.port.FileStorageService;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.example.snow.notebook.domain.Notebook;
import org.example.snow.notebook.infra.NotebookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final NotebookRepository notebookRepository;
    private final SectionRepository sectionRepository;
    private final ChunkRepository chunkRepository;
    private final DocumentAnalysisService documentAnalysisService;
    private final FileStorageService fileStorageService;
    private final GeneratedQuizRepository generatedQuizRepository;
    private final GenerationJobRepository generationJobRepository;
    private final QuizQaHistoryRepository quizQaHistoryRepository;
    private final NotebookQaHistoryRepository notebookQaHistoryRepository;

    @Transactional(readOnly = true)
    public List<Document> getDocuments(Long userId, Long notebookId) {
        Notebook notebook = getNotebookWithOwnershipCheck(userId, notebookId);
        return documentRepository.findAllByNotebook_NotebookIdAndDeletedAtIsNullOrderByUploadedAtAsc(notebook.getNotebookId());
    }

    @Transactional
    public Document createDocument(Long userId, Long notebookId, DocumentUploadCommand command) {
        Notebook notebook = getNotebookWithOwnershipCheck(userId, notebookId);
        UploadedDocument file = command.file();
        String fileType = resolveFileType(file.contentType(), file.originalFilename());
        String storedKey = fileStorageService.upload(file.content(), file.contentType(), file.originalFilename(), notebookId);
        Document document = Document.create(
                notebook,
                file.originalFilename(),
                storedKey,
                fileType,
                (long) file.content().length
        );
        Document saved = documentRepository.save(document);
        Long savedId = saved.getDocumentId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                documentAnalysisService.analyzeAsync(savedId, command);
            }
        });
        return saved;
    }

    @Transactional(readOnly = true)
    public Document getDocument(Long userId, Long notebookId, Long documentId) {
        getNotebookWithOwnershipCheck(userId, notebookId);
        return getActiveDocumentWithNotebookCheck(documentId, notebookId);
    }

    // polling 전용: FAILED 직후 soft delete된 문서도 조회 가능해야 하므로 deletedAt 필터 제외
    @Transactional(readOnly = true)
    public Document getDocumentStatus(Long userId, Long notebookId, Long documentId) {
        getNotebookWithOwnershipCheck(userId, notebookId);
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
        if (!document.getNotebook().getNotebookId().equals(notebookId)) {
            throw new BusinessException(ErrorCode.DOCUMENT_ACCESS_DENIED);
        }
        return document;
    }

    @Transactional(readOnly = true)
    public List<Section> getSections(Long userId, Long notebookId, Long documentId) {
        getNotebookWithOwnershipCheck(userId, notebookId);
        Document document = getActiveDocumentWithNotebookCheck(documentId, notebookId);
        if (document.getAnalysisStatus() != AnalysisStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_COMPLETED);
        }
        return sectionRepository.findAllByDocument_DocumentIdAndDeletedAtIsNullOrderBySectionOrderAsc(documentId);
    }

    @Transactional
    public void deleteDocument(Long userId, Long notebookId, Long documentId) {
        getNotebookWithOwnershipCheck(userId, notebookId);
        Document document = getActiveDocumentWithNotebookCheck(documentId, notebookId);

        if (document.getAnalysisStatus() == AnalysisStatus.ANALYZING) {
            throw new BusinessException(ErrorCode.DOCUMENT_ANALYZING);
        }

        LocalDateTime now = LocalDateTime.now();
        chunkRepository.nullEmbeddingByDocumentId(documentId);
        chunkRepository.softDeleteByDocumentId(documentId, now);
        sectionRepository.softDeleteByDocumentId(documentId, now);
        generatedQuizRepository.clearSourceSectionIdsByDocumentId(documentId);
        notebookQaHistoryRepository.clearCitedSectionIdsByDocumentId(documentId);
        document.softDelete();
    }

    @Transactional
    public void cascadeDeleteByNotebook(Long notebookId) {
        LocalDateTime now = LocalDateTime.now();
        chunkRepository.nullEmbeddingByNotebookId(notebookId);
        chunkRepository.softDeleteByNotebookId(notebookId, now);
        sectionRepository.softDeleteByNotebookId(notebookId, now);
        documentRepository.softDeleteByNotebookId(notebookId, now);
        quizQaHistoryRepository.softDeleteByNotebookId(notebookId, now);
        generatedQuizRepository.softDeleteByNotebookId(notebookId, now);
        generationJobRepository.softDeleteByNotebookId(notebookId, now);
        notebookQaHistoryRepository.softDeleteByNotebookId(notebookId, now);
    }

    private Notebook getNotebookWithOwnershipCheck(Long userId, Long notebookId) {
        Notebook notebook = notebookRepository.findByNotebookIdAndDeletedAtIsNull(notebookId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTEBOOK_NOT_FOUND));
        if (!notebook.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOTEBOOK_ACCESS_DENIED);
        }
        return notebook;
    }

    private Document getActiveDocumentWithNotebookCheck(Long documentId, Long notebookId) {
        Document document = documentRepository.findByDocumentIdAndDeletedAtIsNull(documentId)
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
