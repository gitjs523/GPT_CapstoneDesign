package org.example.snow.document.application;

import org.example.snow.ai.application.OllamaService;
import org.example.snow.document.application.chunking.DocumentTitleResolver;
import org.example.snow.document.application.chunking.SemanticSectionizer;
import org.example.snow.document.application.chunking.SemanticSectionizer.ChunkVector;
import org.example.snow.document.application.chunking.SemanticSectionizer.SectionGroup;
import org.example.snow.document.application.chunking.TextBlock;
import org.example.snow.document.domain.Chunk;
import org.example.snow.document.domain.Document;
import org.example.snow.document.domain.ExtractedChunk;
import org.example.snow.document.domain.ExtractedDocument;
import org.example.snow.document.domain.ExtractedSection;
import org.example.snow.document.domain.ExtractedSourceUnit;
import org.example.snow.document.domain.Section;
import org.example.snow.document.domain.SourceUnitType;
import org.example.snow.document.infra.ChunkRepository;
import org.example.snow.document.infra.DocumentRepository;
import org.example.snow.document.infra.SectionRepository;
import org.example.snow.document.infra.SourceUnitRepository;
import org.example.snow.embedding.application.EmbeddingService;
import org.example.snow.global.exception.ErrorCode;
import org.example.snow.notebook.domain.Notebook;
import org.example.snow.user.domain.UserAccount;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentAnalysisServiceTest {

    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final SourceUnitRepository sourceUnitRepository = mock(SourceUnitRepository.class);
    private final SectionRepository sectionRepository = mock(SectionRepository.class);
    private final ChunkRepository chunkRepository = mock(ChunkRepository.class);
    private final DocumentIngestionService documentIngestionService = mock(DocumentIngestionService.class);
    private final OllamaService ollamaService = mock(OllamaService.class);
    private final EmbeddingService embeddingService = mock(EmbeddingService.class);
    private final DocumentAnalysisStatusManager statusManager = mock(DocumentAnalysisStatusManager.class);
    private final SemanticSectionizer semanticSectionizer = mock(SemanticSectionizer.class);
    private final DocumentTitleResolver documentTitleResolver = mock(DocumentTitleResolver.class);

    private final DocumentAnalysisService service = new DocumentAnalysisService(
            documentRepository,
            sourceUnitRepository,
            sectionRepository,
            chunkRepository,
            documentIngestionService,
            ollamaService,
            embeddingService,
            statusManager,
            semanticSectionizer,
            documentTitleResolver
    );

    // ─────────────────────── 정상 흐름 ───────────────────────

    @Test
    void analyze_정상흐름_임베딩후_분할_저장_completeAnalysis() {
        Document document = createDocument(10L);
        DocumentUploadCommand command = createCommand();
        when(documentRepository.findById(10L)).thenReturn(Optional.of(document));
        when(documentIngestionService.ingest(command)).thenReturn(ingested(block(1), block(2)));
        when(embeddingService.embedAll(any())).thenReturn(List.of(vector(), vector()));
        when(documentTitleResolver.resolve(any(), any(), any())).thenReturn("문서제목");
        when(semanticSectionizer.sectionize(any(), any(), any()))
                .thenReturn(List.of(sectionGroup(1, 1), sectionGroup(2, 1)));
        when(sectionRepository.saveAll(any())).thenReturn(List.of(createSection(document, 100L), createSection(document, 101L)));
        when(ollamaService.generateSummary(any())).thenReturn("요약 텍스트");

        service.analyze(10L, command);

        verify(sourceUnitRepository).saveAll(any());
        verify(embeddingService).embedAll(any());
        verify(sectionRepository).saveAll(any());
        verify(chunkRepository).saveAll(any());
        verify(statusManager).completeAnalysis(eq(10L), eq("요약 텍스트"), eq(2));
        verify(statusManager, never()).markFailed(any(), any());
        assertThat(document.getTitle()).isEqualTo("문서제목");
    }

    @Test
    void analyze_요약생성_실패해도_null_summary로_completeAnalysis() {
        Document document = createDocument(10L);
        DocumentUploadCommand command = createCommand();
        when(documentRepository.findById(10L)).thenReturn(Optional.of(document));
        when(documentIngestionService.ingest(command)).thenReturn(ingested(block(1)));
        when(embeddingService.embedAll(any())).thenReturn(List.of(vector()));
        when(documentTitleResolver.resolve(any(), any(), any())).thenReturn("문서제목");
        when(semanticSectionizer.sectionize(any(), any(), any())).thenReturn(List.of(sectionGroup(1, 1)));
        when(sectionRepository.saveAll(any())).thenReturn(List.of(createSection(document, 100L)));
        when(ollamaService.generateSummary(any())).thenThrow(new RuntimeException("LLM 호출 실패"));

        service.analyze(10L, command);

        verify(statusManager).completeAnalysis(eq(10L), isNull(), eq(1));
        verify(statusManager, never()).markFailed(any(), any());
    }

    // ─────────────────────── 실패 흐름 ───────────────────────

    @Test
    void analyze_document_없으면_markFailed() {
        when(documentRepository.findById(10L)).thenReturn(Optional.empty());

        service.analyze(10L, createCommand());

        verify(statusManager).markFailed(eq(10L), any());
        verify(documentIngestionService, never()).ingest(any());
        verify(statusManager, never()).completeAnalysis(any(), any(), anyInt());
    }

    @Test
    void analyze_블록이_0개이면_DOCUMENT_EMPTY_CONTENT로_markFailed() {
        Document document = createDocument(10L);
        DocumentUploadCommand command = createCommand();
        when(documentRepository.findById(10L)).thenReturn(Optional.of(document));
        when(documentIngestionService.ingest(command)).thenReturn(ingested());

        service.analyze(10L, command);

        verify(statusManager).markFailed(eq(10L), eq(ErrorCode.DOCUMENT_EMPTY_CONTENT.getMessage()));
        verify(embeddingService, never()).embedAll(any());
        verify(sectionRepository, never()).saveAll(any());
        verify(statusManager, never()).completeAnalysis(any(), any(), anyInt());
    }

    @Test
    void analyze_ingest_실패시_markFailed() {
        Document document = createDocument(10L);
        when(documentRepository.findById(10L)).thenReturn(Optional.of(document));
        when(documentIngestionService.ingest(any())).thenThrow(new RuntimeException("텍스트 추출 실패"));

        service.analyze(10L, createCommand());

        verify(statusManager).markFailed(eq(10L), eq("텍스트 추출 실패"));
        verify(sectionRepository, never()).saveAll(any());
        verify(statusManager, never()).completeAnalysis(any(), any(), anyInt());
    }

    // ─────────────────────── 매핑 검증 ───────────────────────

    @Test
    void analyze_chunk이_소속_SectionGroup의_parent_section에_매핑되고_임베딩이_세팅됨() {
        Document document = createDocument(10L);
        DocumentUploadCommand command = createCommand();
        Section section1 = createSection(document, 100L);
        Section section2 = createSection(document, 101L);
        when(documentRepository.findById(10L)).thenReturn(Optional.of(document));
        when(documentIngestionService.ingest(command)).thenReturn(ingested(block(1), block(2)));
        when(embeddingService.embedAll(any())).thenReturn(List.of(vector(), vector()));
        when(documentTitleResolver.resolve(any(), any(), any())).thenReturn("문서제목");
        when(semanticSectionizer.sectionize(any(), any(), any()))
                .thenReturn(List.of(sectionGroup(1, 1), sectionGroup(2, 1)));
        when(sectionRepository.saveAll(any())).thenReturn(List.of(section1, section2));
        when(ollamaService.generateSummary(any())).thenReturn("요약");

        service.analyze(10L, command);

        ArgumentCaptor<List<Chunk>> captor = ArgumentCaptor.forClass(List.class);
        verify(chunkRepository).saveAll(captor.capture());
        List<Chunk> savedChunks = captor.getValue();
        assertThat(savedChunks).hasSize(2);
        assertThat(savedChunks.get(0).getSection()).isSameAs(section1);
        assertThat(savedChunks.get(1).getSection()).isSameAs(section2);
        assertThat(savedChunks.get(0).getEmbedding()).isNotNull();
    }

    // ─────────────────────── 헬퍼 ────────────────────────────

    private Document createDocument(Long documentId) {
        UserAccount user = UserAccount.create("test@example.com", "hash");
        Notebook notebook = Notebook.create(user, "테스트 노트북");
        Document doc = Document.create(notebook, "lecture.pdf", "stored/lecture.pdf", "PDF", 1024L);
        ReflectionTestUtils.setField(doc, "documentId", documentId);
        return doc;
    }

    private Section createSection(Document document, Long sectionId) {
        Section section = Section.create(document, new ExtractedSection(
                1, "헤딩", "내용", SourceUnitType.PAGE, 1, 1, List.of(1)
        ));
        ReflectionTestUtils.setField(section, "sectionId", sectionId);
        return section;
    }

    private DocumentUploadCommand createCommand() {
        return new DocumentUploadCommand(
                new UploadedDocument("lecture.pdf", "application/pdf", "content".getBytes())
        );
    }

    private TextBlock block(int page) {
        return new TextBlock(page, "페이지 " + page + " 본문 블록 내용", SourceUnitType.PAGE);
    }

    private float[] vector() {
        return new float[]{0.1f, 0.2f, 0.3f};
    }

    private SectionGroup sectionGroup(int order, int page) {
        ExtractedSection section = new ExtractedSection(
                order, "섹션 " + order, "섹션 내용", SourceUnitType.PAGE, page, page, List.of(page));
        ExtractedChunk chunk = new ExtractedChunk(
                order, "섹션 " + order, "청크 내용", SourceUnitType.PAGE, page, page, List.of(page));
        return new SectionGroup(section, List.of(new ChunkVector(chunk, vector())));
    }

    private IngestedDocument ingested(TextBlock... blocks) {
        // sourceUnit(=페이지) 수는 블록의 distinct page 수로 맞춘다 (completeAnalysis의 sourceUnitCount 검증용)
        List<ExtractedSourceUnit> units = java.util.stream.Stream.of(blocks)
                .map(TextBlock::page).distinct().sorted()
                .map(p -> new ExtractedSourceUnit(p, "Page " + p, "페이지 " + p + " 내용"))
                .toList();
        ExtractedDocument extractedDocument = new ExtractedDocument(
                "lecture.pdf", "application/pdf", SourceUnitType.PAGE, units);
        return new IngestedDocument(extractedDocument, List.of(), List.of(blocks));
    }
}
