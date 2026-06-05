package org.example.snow.document.application;

import org.example.snow.document.application.chunking.BlockSplitter;
import org.example.snow.document.application.chunking.BoilerplateFilter;
import org.example.snow.document.application.chunking.TextBlock;
import org.example.snow.document.application.port.TextExtractor;
import org.example.snow.document.domain.ExtractedDocument;
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

    private final DocumentIngestionService service = new DocumentIngestionService(
            List.of(extractor),
            textPreprocessor,
            new BoilerplateFilter(),
            new BlockSplitter()
    );

    // ─────────────────────── 파일 유효성 검사 ───────────────────────

    @Test
    void ingest_command_null이면_FILE_REQUIRED_예외() {
        assertThatThrownBy(() -> service.ingest(null))
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
                new BoilerplateFilter(),
                new BlockSplitter()
        );
        ExtractedDocument extracted = doc(List.of(new ExtractedSourceUnit(1, "Page 1", "내용")));
        when(unsupported.supports(any())).thenReturn(false);
        when(supported.supports(any())).thenReturn(true);
        when(supported.extract(any())).thenReturn(extracted);
        when(textPreprocessor.normalize(any())).thenReturn("내용");

        serviceWithMultiple.ingest(pdfCommand());

        verify(unsupported, never()).extract(any());
        verify(supported).extract(any());
    }

    // ─────────────────────── 전처리 ─────────────────────────────────

    @Test
    void ingest_textPreprocessor_실패시_DOCUMENT_PREPROCESSING_FAILED_예외() {
        ExtractedDocument extracted = doc(List.of(new ExtractedSourceUnit(1, "Page 1", "원본 텍스트")));
        when(extractor.supports(any())).thenReturn(true);
        when(extractor.extract(any())).thenReturn(extracted);
        when(textPreprocessor.normalize(any())).thenThrow(new RuntimeException("정규화 실패"));

        assertThatThrownBy(() -> service.ingest(pdfCommand()))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.DOCUMENT_PREPROCESSING_FAILED.getMessage());
    }

    @Test
    void ingest_정규화된_텍스트가_extractedDocument_sourceUnit에_반영됨() {
        ExtractedDocument extracted = doc(List.of(new ExtractedSourceUnit(1, "Page 1", "원본  텍스트\r\n")));
        when(extractor.supports(any())).thenReturn(true);
        when(extractor.extract(any())).thenReturn(extracted);
        when(textPreprocessor.normalize("원본  텍스트\r\n")).thenReturn("원본 텍스트");

        IngestedDocument result = service.ingest(pdfCommand());

        assertThat(result.extractedDocument().sourceUnits().get(0).text()).isEqualTo("원본 텍스트");
    }

    // ─────────────────────── 블록 분리 ──────────────────────────────

    @Test
    void ingest_문단은_빈줄_경계로_블록이_된다() {
        String para1 = "첫 번째 문단의 본문 내용이 충분히 길게 담겨 있어서 마흔 글자를 넘기므로 독립된 하나의 블록을 이룬다.";
        String para2 = "두 번째 문단의 본문 내용도 마흔 글자를 충분히 넘겨서 흡수되지 않고 별도의 블록으로 분리되어 담긴다.";
        ExtractedDocument extracted = doc(List.of(
                new ExtractedSourceUnit(1, "Page 1", para1 + "\n\n" + para2)
        ));
        when(extractor.supports(any())).thenReturn(true);
        when(extractor.extract(any())).thenReturn(extracted);
        when(textPreprocessor.normalize(any())).thenAnswer(inv -> inv.getArgument(0));

        IngestedDocument result = service.ingest(pdfCommand());

        List<TextBlock> blocks = result.blocks();
        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0).text()).isEqualTo(para1);
        assertThat(blocks.get(1).text()).isEqualTo(para2);
        assertThat(blocks).allSatisfy(b -> {
            assertThat(b.page()).isEqualTo(1);
            assertThat(b.sourceType()).isEqualTo(SourceUnitType.PAGE);
        });
    }

    @Test
    void ingest_blank_페이지는_블록을_만들지_않는다() {
        ExtractedDocument extracted = doc(List.of(
                new ExtractedSourceUnit(1, "Page 1", ""),
                new ExtractedSourceUnit(2, "Page 2", "   ")
        ));
        when(extractor.supports(any())).thenReturn(true);
        when(extractor.extract(any())).thenReturn(extracted);
        when(textPreprocessor.normalize(any())).thenAnswer(inv -> inv.getArgument(0));

        IngestedDocument result = service.ingest(pdfCommand());

        assertThat(result.blocks()).isEmpty();
    }

    // ─────────────────────── 헬퍼 ────────────────────────────────────

    private DocumentUploadCommand pdfCommand() {
        return new DocumentUploadCommand(
                new UploadedDocument("lecture.pdf", "application/pdf", "content".getBytes())
        );
    }

    private ExtractedDocument doc(List<ExtractedSourceUnit> sourceUnits) {
        return new ExtractedDocument("lecture.pdf", "application/pdf", SourceUnitType.PAGE, sourceUnits);
    }
}
