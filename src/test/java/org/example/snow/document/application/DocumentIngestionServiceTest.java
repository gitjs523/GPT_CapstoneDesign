package org.example.snow.document.application;

import org.example.snow.document.application.port.TextExtractor;
import org.example.snow.document.domain.ExtractedChunk;
import org.example.snow.document.domain.ExtractedDocument;
import org.example.snow.document.domain.ExtractedSection;
import org.example.snow.document.domain.ExtractedSourceUnit;
import org.example.snow.document.domain.SourceUnitType;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentIngestionServiceTest {

    private final TextExtractor extractor = mock(TextExtractor.class);
    private final TextPreprocessor textPreprocessor = mock(TextPreprocessor.class);
    private final ChunkingService chunkingService = mock(ChunkingService.class);

    private final DocumentIngestionService service = new DocumentIngestionService(
            List.of(extractor),
            textPreprocessor,
            chunkingService,
            new org.example.snow.document.application.chunking.BoilerplateFilter(),
            new org.example.snow.document.application.chunking.DocumentTitleResolver()
    );

    // ─────────────────────── 파일 유효성 검사 ───────────────────────

    @Test
    void ingest_command_null이면_FILE_REQUIRED_예외() {
        assertThatThrownBy(() -> service.ingest(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.FILE_REQUIRED.getMessage());
    }

    @Test
    void ingest_file_null이면_FILE_REQUIRED_예외() {
        assertThatThrownBy(() -> service.ingest(new DocumentUploadCommand(null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.FILE_REQUIRED.getMessage());
    }

    @Test
    void ingest_file_content_비어있으면_FILE_REQUIRED_예외() {
        DocumentUploadCommand command = new DocumentUploadCommand(
                new UploadedDocument("lecture.pdf", "application/pdf", new byte[0])
        );
        assertThatThrownBy(() -> service.ingest(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.FILE_REQUIRED.getMessage());
    }

    // ─────────────────────── 추출기 선택 ────────────────────────────

    @Test
    void ingest_지원하는_extractor_없으면_UNSUPPORTED_DOCUMENT_TYPE_예외() {
        when(extractor.supports(any())).thenReturn(false);
        DocumentUploadCommand command = new DocumentUploadCommand(
                new UploadedDocument("report.hwp", "application/x-hwp", "content".getBytes())
        );

        assertThatThrownBy(() -> service.ingest(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.UNSUPPORTED_DOCUMENT_TYPE.getMessage());
    }

    @Test
    void ingest_여러_extractor_중_supports_반환하는_첫번째만_사용() {
        TextExtractor unsupported = mock(TextExtractor.class);
        TextExtractor supported = mock(TextExtractor.class);
        DocumentIngestionService serviceWithMultiple = new DocumentIngestionService(
                List.of(unsupported, supported),
                textPreprocessor,
                chunkingService,
                new org.example.snow.document.application.chunking.BoilerplateFilter(),
                new org.example.snow.document.application.chunking.DocumentTitleResolver()
        );
        ExtractedDocument extracted = createExtractedDocument(List.of(
                new ExtractedSourceUnit(1, "Page 1", "내용")
        ));
        when(unsupported.supports(any())).thenReturn(false);
        when(supported.supports(any())).thenReturn(true);
        when(supported.extract(any())).thenReturn(extracted);
        when(textPreprocessor.normalize(any())).thenReturn("내용");
        when(chunkingService.buildSections(any())).thenReturn(List.of());
        when(chunkingService.chunk(any())).thenReturn(List.of());

        serviceWithMultiple.ingest(createPdfCommand());

        verify(unsupported, never()).extract(any());
        verify(supported).extract(any());
    }

    // ─────────────────────── 전처리 ─────────────────────────────────

    @Test
    void ingest_textPreprocessor_실패시_DOCUMENT_PREPROCESSING_FAILED_예외() {
        ExtractedDocument extracted = createExtractedDocument(List.of(
                new ExtractedSourceUnit(1, "Page 1", "원본 텍스트")
        ));
        when(extractor.supports(any())).thenReturn(true);
        when(extractor.extract(any())).thenReturn(extracted);
        when(textPreprocessor.normalize(any())).thenThrow(new RuntimeException("정규화 실패"));

        assertThatThrownBy(() -> service.ingest(createPdfCommand()))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.DOCUMENT_PREPROCESSING_FAILED.getMessage());
    }

    @Test
    void ingest_정규화된_텍스트가_extractedDocument_sourceUnit에_반영됨() {
        ExtractedDocument extracted = createExtractedDocument(List.of(
                new ExtractedSourceUnit(1, "Page 1", "원본  텍스트\r\n")
        ));
        when(extractor.supports(any())).thenReturn(true);
        when(extractor.extract(any())).thenReturn(extracted);
        when(textPreprocessor.normalize("원본  텍스트\r\n")).thenReturn("원본 텍스트");
        when(chunkingService.buildSections(any())).thenReturn(List.of());
        when(chunkingService.chunk(any())).thenReturn(List.of());

        DocumentProcessingResult result = service.ingest(createPdfCommand());

        assertThat(result.extractedDocument().sourceUnits().get(0).text()).isEqualTo("원본 텍스트");
    }

    // ─────────────────────── 전체 흐름 ──────────────────────────────

    @Test
    void ingest_정상흐름_DocumentProcessingResult_필드_검증() {
        ExtractedDocument extracted = createExtractedDocument(List.of(
                new ExtractedSourceUnit(1, "Page 1", "텍스트1"),
                new ExtractedSourceUnit(2, "Page 2", "텍스트2")
        ));
        List<ExtractedSection> sections = List.of(
                new ExtractedSection(1, "섹션1", "내용", SourceUnitType.PAGE, 1, 2, List.of(1, 2))
        );
        List<ExtractedChunk> chunks = List.of(
                new ExtractedChunk(1, "청크1", "내용1", SourceUnitType.PAGE, 1, 1, List.of(1)),
                new ExtractedChunk(2, "청크2", "내용2", SourceUnitType.PAGE, 2, 2, List.of(2))
        );

        when(extractor.supports(any())).thenReturn(true);
        when(extractor.extract(any())).thenReturn(extracted);
        when(textPreprocessor.normalize("텍스트1")).thenReturn("텍스트1");
        when(textPreprocessor.normalize("텍스트2")).thenReturn("텍스트2");
        when(chunkingService.buildSections(any())).thenReturn(sections);
        when(chunkingService.chunk(any())).thenReturn(chunks);

        DocumentProcessingResult result = service.ingest(createPdfCommand());

        assertThat(result.originalFilename()).isEqualTo("lecture.pdf");
        assertThat(result.sourceUnitCount()).isEqualTo(2);
        assertThat(result.sectionCount()).isEqualTo(1);
        assertThat(result.chunkCount()).isEqualTo(2);
        assertThat(result.sections()).isSameAs(sections);
        assertThat(result.chunks()).isSameAs(chunks);
    }

    // ─────────────────────── preprocessedText 조합 ──────────────────

    @Test
    void ingest_sourceUnit_text가_줄바꿈_구분으로_join됨() {
        ExtractedDocument extracted = createExtractedDocument(List.of(
                new ExtractedSourceUnit(1, "Page 1", "첫 번째"),
                new ExtractedSourceUnit(2, "Page 2", "두 번째")
        ));
        when(extractor.supports(any())).thenReturn(true);
        when(extractor.extract(any())).thenReturn(extracted);
        when(textPreprocessor.normalize("첫 번째")).thenReturn("첫 번째");
        when(textPreprocessor.normalize("두 번째")).thenReturn("두 번째");
        when(chunkingService.buildSections(any())).thenReturn(List.of());
        when(chunkingService.chunk(any())).thenReturn(List.of());

        DocumentProcessingResult result = service.ingest(createPdfCommand());

        assertThat(result.preprocessedText()).isEqualTo("첫 번째\n\n두 번째");
    }

    @Test
    void ingest_빈_text_sourceUnit은_preprocessedText에서_제외됨() {
        ExtractedDocument extracted = createExtractedDocument(List.of(
                new ExtractedSourceUnit(1, "Page 1", "내용 있는 페이지"),
                new ExtractedSourceUnit(2, "Page 2", ""),
                new ExtractedSourceUnit(3, "Page 3", "마지막 페이지")
        ));
        when(extractor.supports(any())).thenReturn(true);
        when(extractor.extract(any())).thenReturn(extracted);
        when(textPreprocessor.normalize("내용 있는 페이지")).thenReturn("내용 있는 페이지");
        when(textPreprocessor.normalize("")).thenReturn("");
        when(textPreprocessor.normalize("마지막 페이지")).thenReturn("마지막 페이지");
        when(chunkingService.buildSections(any())).thenReturn(List.of());
        when(chunkingService.chunk(any())).thenReturn(List.of());

        DocumentProcessingResult result = service.ingest(createPdfCommand());

        assertThat(result.preprocessedText()).isEqualTo("내용 있는 페이지\n\n마지막 페이지");
    }

    // ─────────────────────── 헬퍼 ────────────────────────────────────

    private DocumentUploadCommand createPdfCommand() {
        return new DocumentUploadCommand(
                new UploadedDocument("lecture.pdf", "application/pdf", "content".getBytes())
        );
    }

    private ExtractedDocument createExtractedDocument(List<ExtractedSourceUnit> sourceUnits) {
        return new ExtractedDocument("lecture.pdf", "application/pdf", SourceUnitType.PAGE, sourceUnits);
    }
}
