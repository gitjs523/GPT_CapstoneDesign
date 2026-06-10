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
import org.example.snow.document.infra.SourceUnitRepository;
import org.example.snow.document.application.port.FileStorageService;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.example.snow.global.queue.ModelQueueService;
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
    private final SourceUnitRepository sourceUnitRepository;
    private final SectionRepository sectionRepository;
    private final ChunkRepository chunkRepository;
    private final DocumentAnalysisService documentAnalysisService;
    private final DocumentAnalysisStatusManager statusManager;
    private final FileStorageService fileStorageService;
    private final ModelQueueService modelQueueService;
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

        if (!modelQueueService.canAcceptEmbedding()) {
            throw new BusinessException(ErrorCode.MODEL_QUEUE_FULL);
        }

        UploadedDocument file = command.file();
        String fileType = resolveFileType(file.contentType(), file.originalFilename());
        if ("UNKNOWN".equals(fileType)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_DOCUMENT_TYPE);
        }
        String storedKey = fileStorageService.upload(file.content(), file.contentType(), file.originalFilename(), notebookId);
        Document document = Document.create(
                notebook,
                file.originalFilename(),
                storedKey,
                fileType,
                (long) file.content().length
        );
        Document saved = documentRepository.save(document);
        saved.startAnalysis();
        Long savedId = saved.getDocumentId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                boolean submitted = modelQueueService.submitEmbedding(
                        () -> documentAnalysisService.analyze(savedId, command)
                );
                if (!submitted) {
                    // canAcceptEmbedding() 통과 후 극히 드문 race condition 대비
                    statusManager.markFailed(savedId, "임베딩 큐가 가득 찼습니다.");
                }
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

        if (document.getAnalysisStatus() == AnalysisStatus.ANALYZING
                || document.getAnalysisStatus() == AnalysisStatus.SUMMARIZING) {
            throw new BusinessException(ErrorCode.DOCUMENT_ANALYZING);
        }

        LocalDateTime now = LocalDateTime.now();
        sourceUnitRepository.deleteAllByDocumentId(documentId);
        chunkRepository.nullEmbeddingByDocumentId(documentId);
        chunkRepository.softDeleteByDocumentId(documentId, now);
        sectionRepository.softDeleteByDocumentId(documentId, now);
        generatedQuizRepository.clearSourceSectionIdsByDocumentId(documentId);
        notebookQaHistoryRepository.clearCitedSectionIdsByDocumentId(documentId);
        document.softDelete();
        documentRepository.save(document);
    }

    public void validateNoneAnalyzing(Long notebookId) {
        if (documentRepository.existsByNotebook_NotebookIdAndAnalysisStatusInAndDeletedAtIsNull(
                notebookId, IN_PROGRESS_STATUSES)) {
            throw new BusinessException(ErrorCode.DOCUMENT_ANALYZING);
        }
    }

    /**
     * 노트북에 분석 완료(COMPLETED) 문서가 1개도 없으면 거부한다.
     * 문제 생성·질문 모두 재료가 될 분석 완료 문서가 없으면 진행 불가다.
     * {@link #validateNoneAnalyzing} 이후에 호출해, 분석 중 문서가 있으면 'DOCUMENT_ANALYZING'이
     * 먼저 안내되고, 분석 중도 완료도 없는 빈/실패 노트북에서만 이 예외가 뜨도록 한다.
     */
    public void validateHasAnalyzedDocument(Long notebookId) {
        if (!documentRepository.existsByNotebook_NotebookIdAndAnalysisStatusAndDeletedAtIsNull(
                notebookId, AnalysisStatus.COMPLETED)) {
            throw new BusinessException(ErrorCode.NO_ANALYZED_DOCUMENT);
        }
    }

    /**
     * 요청한 문제 수가 생성 가능한 최대치를 넘으면 거부한다 (function-specs #32).
     * 상한 = 노트북 내 분석 완료(COMPLETED) 문서의 전체 Section 수. quiz 1개당 의미 단위 Section
     * 1개에 대응시켜 중복 문제를 막으려는 정책이라, 가진 Section 수보다 많은 문제는 만들 수 없다.
     * (DTO의 정적 @Max(20)는 운영 보호용 전역 가드로 별도 적용되어, 실효 상한은 min(Section 수, 20)다.)
     * {@link #validateHasAnalyzedDocument} 이후에 호출한다(분석 완료 문서가 있으면 Section ≥ 1 보장).
     */
    public void validateQuizCountWithinSectionLimit(Long notebookId, int quizCount) {
        long sectionLimit = sectionRepository.countByNotebookAndDocumentStatus(notebookId, AnalysisStatus.COMPLETED);
        if (quizCount > sectionLimit) {
            throw new BusinessException(ErrorCode.QUIZ_COUNT_LIMIT_EXCEEDED);
        }
    }

    public void validateNoneAnalyzingByUser(Long userId) {
        if (documentRepository.existsByNotebook_User_UserIdAndAnalysisStatusInAndDeletedAtIsNull(
                userId, IN_PROGRESS_STATUSES)) {
            throw new BusinessException(ErrorCode.DOCUMENT_ANALYZING);
        }
    }

    private static final java.util.Set<AnalysisStatus> IN_PROGRESS_STATUSES =
            java.util.Set.of(AnalysisStatus.ANALYZING, AnalysisStatus.SUMMARIZING);

    @Transactional
    public void cascadeDeleteByNotebook(Long notebookId) {
        LocalDateTime now = LocalDateTime.now();
        sourceUnitRepository.deleteAllByNotebookId(notebookId);
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
