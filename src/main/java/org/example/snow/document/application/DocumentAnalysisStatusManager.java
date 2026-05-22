package org.example.snow.document.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.snow.document.domain.Document;
import org.example.snow.document.infra.ChunkRepository;
import org.example.snow.document.infra.DocumentRepository;
import org.example.snow.document.infra.SectionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentAnalysisStatusManager {

    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final SectionRepository sectionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeAnalysis(Long documentId, String summaryText, int pageCount) {
        documentRepository.findById(documentId).ifPresent(doc -> {
            doc.saveSummary(summaryText);
            doc.completeAnalysis(pageCount);
            documentRepository.save(doc);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long documentId, String errorMessage) {
        documentRepository.findById(documentId).ifPresent(doc -> {
            doc.failAnalysis(errorMessage);
            doc.softDelete();
            LocalDateTime now = LocalDateTime.now();
            chunkRepository.softDeleteByDocumentId(documentId, now);
            sectionRepository.softDeleteByDocumentId(documentId, now);
            documentRepository.save(doc);
            log.error("Document analysis failed and soft-deleted. documentId={} error={}", documentId, errorMessage);
        });
    }
}
