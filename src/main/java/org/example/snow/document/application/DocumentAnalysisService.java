package org.example.snow.document.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.snow.ai.application.OllamaService;
import org.example.snow.document.application.chunking.DocumentTitleResolver;
import org.example.snow.document.application.chunking.SemanticSectionizer;
import org.example.snow.document.application.chunking.SemanticSectionizer.ChunkVector;
import org.example.snow.document.application.chunking.SemanticSectionizer.SectionGroup;
import org.example.snow.document.application.chunking.TextBlock;
import org.example.snow.document.domain.Chunk;
import org.example.snow.document.domain.Document;
import org.example.snow.document.domain.ExtractedDocument;
import org.example.snow.document.domain.ExtractedSection;
import org.example.snow.document.domain.Section;
import org.example.snow.document.domain.SourceUnit;
import org.example.snow.document.infra.ChunkRepository;
import org.example.snow.document.infra.DocumentRepository;
import org.example.snow.document.infra.SectionRepository;
import org.example.snow.document.infra.SourceUnitRepository;
import org.example.snow.embedding.application.EmbeddingService;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 문서 분석 파이프라인 오케스트레이션.
 *
 * ingest(추출→전처리→boilerplate→블록) → 블록 임베딩 → semantic 분할(경계+cap+label)
 * → Section/Chunk 영속(블록 임베딩을 chunk 임베딩으로 그대로 저장) → 요약 → 완료.
 *
 * 임베딩이 분할의 선행 조건이므로 임베딩 단계가 sectioning 앞에 온다(종전엔 sectioning 후 임베딩).
 */
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
    private final SemanticSectionizer semanticSectionizer;
    private final DocumentTitleResolver documentTitleResolver;

    public void analyze(Long documentId, DocumentUploadCommand command) {
        try {
            Document document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
            analyzeInternal(document, command);
        } catch (Exception e) {
            log.error("Document analysis failed for documentId={}", documentId, e);
            statusManager.markFailed(documentId, e.getMessage());
        }
    }

    private void analyzeInternal(Document document, DocumentUploadCommand command) {
        IngestedDocument ingested = documentIngestionService.ingest(command);

        if (ingested.blocks().isEmpty()) {
            throw new BusinessException(ErrorCode.DOCUMENT_EMPTY_CONTENT);
        }

        saveSourceUnits(document, ingested.extractedDocument());

        // 블록 임베딩 (단일 패스: 분할 + chunk 저장 공용)
        List<TextBlock> blocks = ingested.blocks();
        List<float[]> embeddings = embeddingService.embedAll(blocks.stream().map(TextBlock::text).toList());

        // semantic 분할
        String filename = ingested.extractedDocument().originalFilename();
        String docTitleHint = documentTitleResolver.resolve(ingested.repeatedLines(), List.of(), filename);
        List<SectionGroup> groups = semanticSectionizer.sectionize(blocks, embeddings, docTitleHint);

        // 최종 docTitle은 실제 Section을 fallback으로 활용해 결정
        List<ExtractedSection> extractedSections = groups.stream()
                .map(SectionGroup::section)
                .toList();
        String docTitle = documentTitleResolver.resolve(ingested.repeatedLines(), extractedSections, filename);
        document.assignTitle(docTitle);

        persist(document, groups);

        statusManager.markSummarizing(document.getDocumentId(), docTitle);

        String summaryText = null;
        try {
            summaryText = ollamaService.generateSummary(buildSummaryInput(extractedSections));
        } catch (Exception e) {
            log.warn("Summary generation failed for documentId={}, completing without summary",
                    document.getDocumentId(), e);
        }
        statusManager.completeAnalysis(
                document.getDocumentId(), summaryText, ingested.extractedDocument().sourceUnits().size());
    }

    private void persist(Document document, List<SectionGroup> groups) {
        List<Section> sectionsToSave = groups.stream()
                .map(group -> Section.create(document, group.section()))
                .toList();
        List<Section> savedSections = sectionRepository.saveAll(sectionsToSave);

        List<Chunk> chunksToSave = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            Section parentSection = savedSections.get(i);
            for (ChunkVector chunkVector : groups.get(i).chunks()) {
                Chunk chunk = Chunk.create(parentSection, document, chunkVector.chunk());
                chunk.updateEmbedding(chunkVector.embedding());
                chunksToSave.add(chunk);
            }
        }
        chunkRepository.saveAll(chunksToSave);
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
}
