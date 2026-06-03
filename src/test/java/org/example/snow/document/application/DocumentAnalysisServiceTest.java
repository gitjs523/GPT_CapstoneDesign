package org.example.snow.document.application;

import org.example.snow.ai.application.OllamaService;
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

    private final DocumentAnalysisService service = new DocumentAnalysisService(
            documentRepository,
            sourceUnitRepository,
            sectionRepository,
            chunkRepository,
            documentIngestionService,
            ollamaService,
            embeddingService,
            statusManager
    );

    // ─────────────────────── 정상 흐름 ───────────────────────

    @Test
    void analyze_정상흐름_모든_저장_호출_후_completeAnalysis_실행() {
        Document document = createDocument(10L);
        DocumentUploadCommand command = createCommand();
        DocumentProcessingResult result = createResult(
                List.of(sourceUnit(1), sourceUnit(2)),
                List.of(extractedSection(1, List.of(1)), extractedSection(2, List.of(2))),
                List.of(extractedChunk(1), extractedChunk(2))
        );
        when(documentRepository.findById(10L)).thenReturn(Optional.of(document));
        when(documentIngestionService.ingest(command)).thenReturn(result);
        when(sectionRepository.saveAll(any())).thenReturn(List.of(createSection(document, 100L), createSection(document, 101L)));
        when(chunkRepository.saveAll(any())).thenReturn(List.of());
        when(ollamaService.generateSummary(any())).thenReturn("요약 텍스트");

        service.analyze(10L, command);

        verify(sourceUnitRepository).saveAll(any());
        verify(sectionRepository).saveAll(any());
        verify(chunkRepository).saveAll(any());
        verify(embeddingService).saveEmbeddings(any());
        verify(statusManager).completeAnalysis(eq(10L), eq("요약 텍스트"), eq(2));
        verify(statusManager, never()).markFailed(any(), any());
    }

    @Test
    void analyze_요약생성_실패해도_null_summary로_completeAnalysis_호출() {
        Document document = createDocument(10L);
        DocumentUploadCommand command = createCommand();
        DocumentProcessingResult result = createResult(
                List.of(sourceUnit(1)),
                List.of(extractedSection(1, List.of(1))),
                List.of(extractedChunk(1))
        );
        when(documentRepository.findById(10L)).thenReturn(Optional.of(document));
        when(documentIngestionService.ingest(command)).thenReturn(result);
        when(sectionRepository.saveAll(any())).thenReturn(List.of(createSection(document, 100L)));
        when(chunkRepository.saveAll(any())).thenReturn(List.of());
        when(ollamaService.generateSummary(any())).thenThrow(new RuntimeException("LLM 호출 실패"));

        service.analyze(10L, command);

        verify(statusManager).completeAnalysis(eq(10L), isNull(), eq(1));
        verify(statusManager, never()).markFailed(any(), any());
    }

    // ─────────────────────── 실패 흐름 ───────────────────────

    @Test
    void analyze_document_없으면_markFailed_호출() {
        when(documentRepository.findById(10L)).thenReturn(Optional.empty());

        service.analyze(10L, createCommand());

        verify(statusManager).markFailed(eq(10L), any());
        verify(documentIngestionService, never()).ingest(any());
        verify(statusManager, never()).completeAnalysis(any(), any(), anyInt());
    }

    @Test
    void analyze_section이_0개이면_DOCUMENT_EMPTY_CONTENT로_markFailed_호출() {
        Document document = createDocument(10L);
        DocumentUploadCommand command = createCommand();
        DocumentProcessingResult result = createResult(
                List.of(sourceUnit(1), sourceUnit(2)),
                List.of(),   // 섹션 0개 — 전체 공백 문서
                List.of()
        );
        when(documentRepository.findById(10L)).thenReturn(Optional.of(document));
        when(documentIngestionService.ingest(command)).thenReturn(result);

        service.analyze(10L, command);

        verify(statusManager).markFailed(eq(10L), eq(ErrorCode.DOCUMENT_EMPTY_CONTENT.getMessage()));
        verify(sourceUnitRepository, never()).saveAll(any());
        verify(sectionRepository, never()).saveAll(any());
        verify(statusManager, never()).completeAnalysis(any(), any(), anyInt());
    }

    @Test
    void analyze_ingest_실패시_markFailed_호출() {
        Document document = createDocument(10L);
        when(documentRepository.findById(10L)).thenReturn(Optional.of(document));
        when(documentIngestionService.ingest(any())).thenThrow(new RuntimeException("텍스트 추출 실패"));

        service.analyze(10L, createCommand());

        verify(statusManager).markFailed(eq(10L), eq("텍스트 추출 실패"));
        verify(sectionRepository, never()).saveAll(any());
        verify(statusManager, never()).completeAnalysis(any(), any(), anyInt());
    }

    @Test
    void analyze_chunk의_sourceStartIndex가_어떤_section에도_없으면_markFailed_호출() {
        // extractedSection sourceIndices=[1] 인데 chunk sourceStartIndex=99 로 매핑 불가
        Document document = createDocument(10L);
        DocumentUploadCommand command = createCommand();
        DocumentProcessingResult result = createResult(
                List.of(sourceUnit(1)),
                List.of(extractedSection(1, List.of(1))),
                List.of(extractedChunk(99))
        );
        when(documentRepository.findById(10L)).thenReturn(Optional.of(document));
        when(documentIngestionService.ingest(command)).thenReturn(result);
        when(sectionRepository.saveAll(any())).thenReturn(List.of(createSection(document, 100L)));

        service.analyze(10L, command);

        verify(statusManager).markFailed(eq(10L), any());
        verify(statusManager, never()).completeAnalysis(any(), any(), anyInt());
    }

    // ─────────────────────── 매핑 검증 ───────────────────────

    @Test
    void analyze_chunk이_sourceStartIndex_기준으로_올바른_parent_section에_매핑됨() {
        // chunk1(sourceStartIndex=1) → section1(sourceIndices=[1])
        // chunk2(sourceStartIndex=2) → section2(sourceIndices=[2])
        Document document = createDocument(10L);
        DocumentUploadCommand command = createCommand();
        Section section1 = createSection(document, 100L);
        Section section2 = createSection(document, 101L);
        DocumentProcessingResult result = createResult(
                List.of(sourceUnit(1), sourceUnit(2)),
                List.of(extractedSection(1, List.of(1)), extractedSection(2, List.of(2))),
                List.of(extractedChunk(1), extractedChunk(2))
        );
        when(documentRepository.findById(10L)).thenReturn(Optional.of(document));
        when(documentIngestionService.ingest(command)).thenReturn(result);
        when(sectionRepository.saveAll(any())).thenReturn(List.of(section1, section2));
        when(chunkRepository.saveAll(any())).thenReturn(List.of());
        when(ollamaService.generateSummary(any())).thenReturn("요약");

        service.analyze(10L, command);

        ArgumentCaptor<List<Chunk>> captor = ArgumentCaptor.forClass(List.class);
        verify(chunkRepository).saveAll(captor.capture());
        List<Chunk> savedChunks = captor.getValue();
        assertThat(savedChunks).hasSize(2);
        assertThat(savedChunks.get(0).getSection()).isSameAs(section1);
        assertThat(savedChunks.get(1).getSection()).isSameAs(section2);
    }

    @Test
    void analyze_chunk이_여러_sourceIndices를_가진_section에도_올바르게_매핑됨() {
        // section1이 page 1,2 양쪽을 포함 — chunk(sourceStartIndex=2)도 section1에 매핑
        Document document = createDocument(10L);
        DocumentUploadCommand command = createCommand();
        Section section1 = createSection(document, 100L);
        DocumentProcessingResult result = createResult(
                List.of(sourceUnit(1), sourceUnit(2)),
                List.of(extractedSection(1, List.of(1, 2))),
                List.of(extractedChunk(1), extractedChunk(2))
        );
        when(documentRepository.findById(10L)).thenReturn(Optional.of(document));
        when(documentIngestionService.ingest(command)).thenReturn(result);
        when(sectionRepository.saveAll(any())).thenReturn(List.of(section1));
        when(chunkRepository.saveAll(any())).thenReturn(List.of());
        when(ollamaService.generateSummary(any())).thenReturn("요약");

        service.analyze(10L, command);

        ArgumentCaptor<List<Chunk>> captor = ArgumentCaptor.forClass(List.class);
        verify(chunkRepository).saveAll(captor.capture());
        List<Chunk> savedChunks = captor.getValue();
        assertThat(savedChunks).hasSize(2);
        assertThat(savedChunks.get(0).getSection()).isSameAs(section1);
        assertThat(savedChunks.get(1).getSection()).isSameAs(section1);
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

    private ExtractedSourceUnit sourceUnit(int index) {
        return new ExtractedSourceUnit(index, "Page " + index, "페이지 " + index + " 내용");
    }

    private ExtractedSection extractedSection(int order, List<Integer> sourceIndices) {
        return new ExtractedSection(
                order, "섹션 " + order, "섹션 내용", SourceUnitType.PAGE,
                sourceIndices.get(0), sourceIndices.get(sourceIndices.size() - 1), sourceIndices
        );
    }

    private ExtractedChunk extractedChunk(int sourceStartIndex) {
        return new ExtractedChunk(
                sourceStartIndex, "청크", "청크 내용", SourceUnitType.PAGE,
                sourceStartIndex, sourceStartIndex, List.of(sourceStartIndex)
        );
    }

    private DocumentProcessingResult createResult(
            List<ExtractedSourceUnit> sourceUnits,
            List<ExtractedSection> sections,
            List<ExtractedChunk> chunks
    ) {
        ExtractedDocument extractedDocument = new ExtractedDocument(
                "lecture.pdf", "application/pdf", SourceUnitType.PAGE, sourceUnits
        );
        return new DocumentProcessingResult(
                "lecture.pdf", "application/pdf", "lecture",
                sourceUnits.size(), sections.size(), chunks.size(),
                100, "전체 텍스트",
                extractedDocument, sections, chunks
        );
    }
}
