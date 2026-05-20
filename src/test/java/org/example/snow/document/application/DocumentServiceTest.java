package org.example.snow.document.application;

import org.example.snow.document.domain.AnalysisStatus;
import org.example.snow.document.domain.ChunkStrategy;
import org.example.snow.document.domain.Document;
import org.example.snow.document.domain.ExtractedSection;
import org.example.snow.document.domain.Section;
import org.example.snow.document.domain.SourceUnitType;
import org.example.snow.document.infra.ChunkRepository;
import org.example.snow.document.infra.DocumentRepository;
import org.example.snow.document.infra.SectionRepository;
import org.example.snow.document.application.port.FileStorageService;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.example.snow.notebook.domain.Notebook;
import org.example.snow.notebook.infra.NotebookRepository;
import org.example.snow.user.domain.UserAccount;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentServiceTest {

    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final NotebookRepository notebookRepository = mock(NotebookRepository.class);
    private final SectionRepository sectionRepository = mock(SectionRepository.class);
    private final ChunkRepository chunkRepository = mock(ChunkRepository.class);
    private final DocumentAnalysisService documentAnalysisService = mock(DocumentAnalysisService.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);

    private final DocumentService documentService = new DocumentService(
            documentRepository,
            notebookRepository,
            sectionRepository,
            chunkRepository,
            documentAnalysisService,
            fileStorageService
    );

    @BeforeEach
    void initTransactionSync() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearTransactionSync() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    // ───────────────────────────── getDocuments ──────────────────────────────

    @Test
    void getDocuments_returnsDocumentsForNotebook() {
        Notebook notebook = createNotebook(1L, 10L);
        Document doc = createDocument(notebook, 100L, AnalysisStatus.COMPLETED);
        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));
        when(documentRepository.findAllByNotebook_NotebookIdAndDeletedAtIsNullOrderByUploadedAtAsc(10L))
                .thenReturn(List.of(doc));

        List<Document> result = documentService.getDocuments(1L, 10L);

        assertThat(result).containsExactly(doc);
    }

    @Test
    void getDocuments_throwsWhenNotOwner() {
        Notebook notebook = createNotebook(2L, 10L);
        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));

        assertThatThrownBy(() -> documentService.getDocuments(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.NOTEBOOK_ACCESS_DENIED.getMessage());
    }

    // ───────────────────────────── createDocument ────────────────────────────

    @Test
    void createDocument_savesDocumentAndRegistersAnalysis() {
        Notebook notebook = createNotebook(1L, 10L);
        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));
        when(fileStorageService.upload(any(), any(), any(), any())).thenReturn("stored/lecture.pdf");
        when(documentRepository.save(any())).thenAnswer(inv -> {
            Document doc = inv.getArgument(0);
            ReflectionTestUtils.setField(doc, "documentId", 100L);
            return doc;
        });

        DocumentUploadCommand command = new DocumentUploadCommand(
                new UploadedDocument("lecture.pdf", "application/pdf", "content".getBytes()),
                ChunkStrategy.AUTO
        );
        Document result = documentService.createDocument(1L, 10L, command);

        assertThat(result.getDocumentId()).isEqualTo(100L);
        assertThat(result.getAnalysisStatus()).isEqualTo(AnalysisStatus.UPLOADED);
        assertThat(result.getOriginalFileName()).isEqualTo("lecture.pdf");

        // afterCommit 트리거 → analyzeAsync 호출 확인
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        verify(documentAnalysisService).analyzeAsync(100L, command);
    }

    @Test
    void createDocument_throwsWhenNotOwner() {
        Notebook notebook = createNotebook(2L, 10L);
        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));

        DocumentUploadCommand command = new DocumentUploadCommand(
                new UploadedDocument("lecture.pdf", "application/pdf", "content".getBytes()),
                ChunkStrategy.AUTO
        );

        assertThatThrownBy(() -> documentService.createDocument(1L, 10L, command))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.NOTEBOOK_ACCESS_DENIED.getMessage());

        verify(documentRepository, never()).save(any());
    }

    // ───────────────────────────── getDocument ───────────────────────────────

    @Test
    void getDocument_returnsDocumentWhenOwnerRequests() {
        Notebook notebook = createNotebook(1L, 10L);
        Document doc = createDocument(notebook, 100L, AnalysisStatus.COMPLETED);
        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));
        when(documentRepository.findByDocumentIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(doc));

        Document result = documentService.getDocument(1L, 10L, 100L);

        assertThat(result).isSameAs(doc);
    }

    @Test
    void getDocument_throwsWhenDocumentBelongsToDifferentNotebook() {
        Notebook notebook = createNotebook(1L, 10L);
        Notebook otherNotebook = createNotebook(1L, 99L);
        Document doc = createDocument(otherNotebook, 100L, AnalysisStatus.COMPLETED);
        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));
        when(documentRepository.findByDocumentIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> documentService.getDocument(1L, 10L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.DOCUMENT_ACCESS_DENIED.getMessage());
    }

    @Test
    void getDocument_throwsWhenDocumentNotFound() {
        Notebook notebook = createNotebook(1L, 10L);
        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));
        when(documentRepository.findByDocumentIdAndDeletedAtIsNull(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.getDocument(1L, 10L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.DOCUMENT_NOT_FOUND.getMessage());
    }

    // ─────────────────────────── getDocumentStatus ───────────────────────────

    @Test
    void getDocumentStatus_returnsSoftDeletedDocumentForPolling() {
        Notebook notebook = createNotebook(1L, 10L);
        Document doc = createDocument(notebook, 100L, AnalysisStatus.FAILED);
        doc.softDelete();
        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));
        when(documentRepository.findById(100L)).thenReturn(Optional.of(doc));

        Document result = documentService.getDocumentStatus(1L, 10L, 100L);

        assertThat(result.getAnalysisStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(result.isDeleted()).isTrue();
    }

    @Test
    void getDocumentStatus_throwsWhenDocumentBelongsToDifferentNotebook() {
        Notebook notebook = createNotebook(1L, 10L);
        Notebook otherNotebook = createNotebook(1L, 99L);
        Document doc = createDocument(otherNotebook, 100L, AnalysisStatus.ANALYZING);
        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));
        when(documentRepository.findById(100L)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> documentService.getDocumentStatus(1L, 10L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.DOCUMENT_ACCESS_DENIED.getMessage());
    }

    // ────────────────────────────── getSections ──────────────────────────────

    @Test
    void getSections_returnsSectionsWhenCompleted() {
        Notebook notebook = createNotebook(1L, 10L);
        Document doc = createDocument(notebook, 100L, AnalysisStatus.COMPLETED);
        Section section = createSection(doc, 200L);
        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));
        when(documentRepository.findByDocumentIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(doc));
        when(sectionRepository.findAllByDocument_DocumentIdAndDeletedAtIsNullOrderBySectionOrderAsc(100L))
                .thenReturn(List.of(section));

        List<Section> result = documentService.getSections(1L, 10L, 100L);

        assertThat(result).containsExactly(section);
    }

    @Test
    void getSections_throwsWhenDocumentNotCompleted() {
        Notebook notebook = createNotebook(1L, 10L);
        Document doc = createDocument(notebook, 100L, AnalysisStatus.ANALYZING);
        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));
        when(documentRepository.findByDocumentIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> documentService.getSections(1L, 10L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.DOCUMENT_NOT_COMPLETED.getMessage());
    }

    // ────────────────────────────── deleteDocument ───────────────────────────

    @Test
    void deleteDocument_softDeletesAndCascades() {
        Notebook notebook = createNotebook(1L, 10L);
        Document doc = createDocument(notebook, 100L, AnalysisStatus.COMPLETED);
        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));
        when(documentRepository.findByDocumentIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(doc));

        documentService.deleteDocument(1L, 10L, 100L);

        verify(chunkRepository).softDeleteByDocumentId(any(), any());
        verify(sectionRepository).softDeleteByDocumentId(any(), any());
        assertThat(doc.isDeleted()).isTrue();
    }

    @Test
    void deleteDocument_throwsWhenAnalyzing() {
        Notebook notebook = createNotebook(1L, 10L);
        Document doc = createDocument(notebook, 100L, AnalysisStatus.ANALYZING);
        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));
        when(documentRepository.findByDocumentIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> documentService.deleteDocument(1L, 10L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.DOCUMENT_ANALYZING.getMessage());

        assertThat(doc.isDeleted()).isFalse();
        verify(chunkRepository, never()).softDeleteByDocumentId(any(), any());
    }

    @Test
    void deleteDocument_throwsWhenNotOwner() {
        Notebook notebook = createNotebook(2L, 10L);
        when(notebookRepository.findByNotebookIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(notebook));

        assertThatThrownBy(() -> documentService.deleteDocument(1L, 10L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.NOTEBOOK_ACCESS_DENIED.getMessage());
    }

    // ──────────────────────────── cascadeDelete ──────────────────────────────

    @Test
    void cascadeDeleteByNotebook_softDeletesAllRelatedData() {
        documentService.cascadeDeleteByNotebook(10L);

        verify(chunkRepository).softDeleteByNotebookId(any(), any());
        verify(sectionRepository).softDeleteByNotebookId(any(), any());
        verify(documentRepository).softDeleteByNotebookId(any(), any());
    }

    // ───────────────────────────── helpers ───────────────────────────────────

    private Notebook createNotebook(Long userId, Long notebookId) {
        UserAccount user = UserAccount.create("user" + userId + "@example.com", "hash");
        ReflectionTestUtils.setField(user, "userId", userId);
        Notebook notebook = Notebook.create(user, "강의 노트");
        ReflectionTestUtils.setField(notebook, "notebookId", notebookId);
        return notebook;
    }

    private Document createDocument(Notebook notebook, Long documentId, AnalysisStatus status) {
        Document doc = Document.create(notebook, "lecture.pdf", "stored/lecture.pdf", "PDF", 1024L);
        ReflectionTestUtils.setField(doc, "documentId", documentId);
        ReflectionTestUtils.setField(doc, "analysisStatus", status);
        return doc;
    }

    private Section createSection(Document document, Long sectionId) {
        Section section = Section.create(document, new ExtractedSection(
                1, "1장", "내용", SourceUnitType.PAGE, 1, 1, List.of(1)
        ));
        ReflectionTestUtils.setField(section, "sectionId", sectionId);
        return section;
    }
}
