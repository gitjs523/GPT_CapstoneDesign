package org.example.snow.document.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.snow.ai.application.OllamaService;
import org.example.snow.embedding.application.EmbeddingService;
import org.example.snow.document.domain.Chunk;
import org.example.snow.document.domain.Document;
import org.example.snow.document.domain.ExtractedChunk;
import org.example.snow.document.domain.ExtractedDocument;
import org.example.snow.document.domain.ExtractedSection;
import org.example.snow.document.domain.Section;
import org.example.snow.document.domain.SourceUnit;
import org.example.snow.document.infra.ChunkRepository;
import org.example.snow.document.infra.DocumentRepository;
import org.example.snow.document.infra.SectionRepository;
import org.example.snow.document.infra.SourceUnitRepository;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentAnalysisService {

    private final DocumentRepository documentRepository;
    private final SourceUnitRepository sourceUnitRepository;
    private final SectionRepository sectionRepository;
    private final ChunkRepository chunkRepository;
    private final DocumentIngestionService documentIngestionService;
    private final OllamaService ollamaService;
    private final EmbeddingService embeddingService;
    private final DocumentAnalysisStatusManager statusManager;

    @Async
    public void analyzeAsync(Long documentId, DocumentUploadCommand command) {
        try {
            Document document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
            analyze(document, command);
        } catch (Exception e) {
            log.error("Document analysis failed for documentId={}", documentId, e);
            statusManager.markFailed(documentId, e.getMessage());
        }
    }

    private void analyze(Document document, DocumentUploadCommand command) {
        DocumentProcessingResult result = documentIngestionService.ingest(command);

        saveSourceUnits(document, result.extractedDocument());
        List<Section> savedSections = saveSections(document, result.sections());
        List<Chunk> savedChunks = saveChunks(document, savedSections, result.sections(), result.chunks());

        embeddingService.saveEmbeddings(savedChunks);

        String summaryText = null;
        try {
            summaryText = ollamaService.generateSummary(buildSummaryInput(result.sections()));
        } catch (Exception e) {
            log.warn("Summary generation failed for documentId={}, completing without summary",
                    document.getDocumentId(), e);
        }
        statusManager.completeAnalysis(document.getDocumentId(), summaryText, result.extractedDocument().sourceUnits().size());
    }

    private String buildSummaryInput(List<ExtractedSection> sections) {
        return sections.stream()
                .map(ExtractedSection::text)
                .collect(Collectors.joining("\n\n"));
    }

    private void saveSourceUnits(Document document, ExtractedDocument extractedDocument) {
        List<SourceUnit> sourceUnits = extractedDocument.sourceUnits().stream()
                .map(extracted -> SourceUnit.create(document, extracted, extractedDocument.sourceType()))
                .toList();
        sourceUnitRepository.saveAll(sourceUnits);
    }

    private List<Section> saveSections(Document document, List<ExtractedSection> extractedSections) {
        List<Section> sections = extractedSections.stream()
                .map(extracted -> Section.create(document, extracted))
                .toList();
        return sectionRepository.saveAll(sections);
    }

    private List<Chunk> saveChunks(
            Document document,
            List<Section> savedSections,
            List<ExtractedSection> extractedSections,
            List<ExtractedChunk> extractedChunks
    ) {
        List<Chunk> chunks = extractedChunks.stream()
                .map(extractedChunk -> {
                    Section parentSection = findParentSection(savedSections, extractedSections, extractedChunk);
                    return Chunk.create(parentSection, document, extractedChunk);
                })
                .toList();
        return chunkRepository.saveAll(chunks);
    }

    private Section findParentSection(
            List<Section> savedSections,
            List<ExtractedSection> extractedSections,
            ExtractedChunk chunk
    ) {
        for (int i = 0; i < extractedSections.size(); i++) {
            ExtractedSection extractedSection = extractedSections.get(i);
            if (extractedSection.sourceIndices().contains(chunk.sourceStartIndex())) {
                return savedSections.get(i);
            }
        }
        throw new IllegalStateException(
                "No parent section found for chunk sourceStartIndex=" + chunk.sourceStartIndex()
        );
    }
}
