package org.example.snow.document.application;

import org.example.snow.document.application.chunking.ChunkComposer;
import org.example.snow.document.application.chunking.ChunkStrategyResolver;
import org.example.snow.document.application.chunking.SectionBuilder;
import org.example.snow.document.domain.ChunkStrategy;
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

/**
 * SourceUnit → Section → Chunk 파이프라인 전체를 검증한다.
 *
 * 각 ChunkStrategy가 어떤 입력에서 어떤 섹션/청크를 만들어내는지,
 * 그리고 빈 문서(0 섹션)처럼 비정상적인 입력이 파이프라인을 통과할 수 있는지를 확인한다.
 *
 * 청크 크기 기준 (ChunkComposer 상수):
 *   TARGET_CHUNK_LENGTH = 1_100 (PARAGRAPH 분리 기준)
 *   MAX_CHUNK_LENGTH    = 1_600 (SECTION 분리 기준)
 */
class ChunkingPipelineTest {

    private final ChunkingService chunkingService = new ChunkingService(
            new ChunkStrategyResolver(),
            new SectionBuilder(),
            new ChunkComposer()
    );

    // ─── 빈 문서 (0 섹션) ────────────────────────────────────────────────

    /**
     * 모든 SourceUnit이 blank인 문서는 섹션도 청크도 0개가 된다.
     * DocumentAnalysisService는 이 결과를 보고 DOCUMENT_EMPTY_CONTENT 예외를 던진다.
     */
    @Test
    void 전체_blank_문서는_섹션이_0개이고_청크도_0개() {
        ExtractedDocument document = pdfDocument(List.of(
                new ExtractedSourceUnit(1, "Page 1", ""),
                new ExtractedSourceUnit(2, "Page 2", "   "),
                new ExtractedSourceUnit(3, "Page 3", "\n\n")
        ));

        List<ExtractedSection> sections = chunkingService.buildSections(document);
        List<ExtractedChunk> chunks = chunkingService.chunk(document, sections, ChunkStrategy.SECTION);

        assertThat(sections).isEmpty();
        assertThat(chunks).isEmpty();
    }

    @Test
    void 일부_blank_페이지가_포함된_문서는_내용있는_페이지로만_섹션이_생성된다() {
        ExtractedDocument document = pdfDocument(List.of(
                new ExtractedSourceUnit(1, "Page 1", ""),
                new ExtractedSourceUnit(2, "Page 2", "운영체제는 하드웨어와 소프트웨어 사이의 인터페이스다."),
                new ExtractedSourceUnit(3, "Page 3", "   ")
        ));

        List<ExtractedSection> sections = chunkingService.buildSections(document);

        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).sourceIndices()).containsExactly(2);
    }

    // ─── 전략 해석 ────────────────────────────────────────────────────────

    @Test
    void AUTO_전략은_SECTION으로_해석된다() {
        ChunkStrategy resolved = chunkingService.resolveStrategy(SourceUnitType.PAGE, ChunkStrategy.AUTO);

        assertThat(resolved).isEqualTo(ChunkStrategy.SECTION);
    }

    @Test
    void null_전략은_AUTO로_간주해_SECTION으로_해석된다() {
        ChunkStrategy resolved = chunkingService.resolveStrategy(SourceUnitType.PAGE, null);

        assertThat(resolved).isEqualTo(ChunkStrategy.SECTION);
    }

    // ─── SECTION 전략 ────────────────────────────────────────────────────

    @Test
    void SECTION_전략_헤딩없는_단일_페이지는_섹션_1개_청크_1개() {
        ExtractedDocument document = pdfDocument(List.of(
                new ExtractedSourceUnit(1, "Page 1", "운영체제는 하드웨어와 소프트웨어 사이의 인터페이스다.")
        ));

        List<ExtractedSection> sections = chunkingService.buildSections(document);
        List<ExtractedChunk> chunks = chunkingService.chunk(document, sections, ChunkStrategy.SECTION);

        assertThat(sections).hasSize(1);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).contains("운영체제는");
        assertThat(chunks.get(0).sourceStartIndex()).isEqualTo(1);
        assertThat(chunks.get(0).sourceEndIndex()).isEqualTo(1);
    }

    @Test
    void SECTION_전략_헤딩이_달라지면_새_섹션이_시작된다() {
        ExtractedDocument document = pdfDocument(List.of(
                new ExtractedSourceUnit(1, "Page 1", "1. 프로세스 관리\n프로세스는 실행 중인 프로그램이다."),
                new ExtractedSourceUnit(2, "Page 2", "프로세스 스케줄링은 CPU를 할당하는 과정이다."),
                new ExtractedSourceUnit(3, "Page 3", "2. 메모리 관리\n메모리는 프로세스에 동적으로 할당된다.")
        ));

        List<ExtractedSection> sections = chunkingService.buildSections(document);

        assertThat(sections).hasSize(2);
        assertThat(sections.get(0).heading()).isEqualTo("1. 프로세스 관리");
        assertThat(sections.get(0).sourceStartIndex()).isEqualTo(1);
        assertThat(sections.get(0).sourceEndIndex()).isEqualTo(2);
        assertThat(sections.get(0).sourceIndices()).containsExactly(1, 2);
        assertThat(sections.get(1).heading()).isEqualTo("2. 메모리 관리");
        assertThat(sections.get(1).sourceStartIndex()).isEqualTo(3);
        assertThat(sections.get(1).sourceEndIndex()).isEqualTo(3);
        assertThat(sections.get(1).sourceIndices()).containsExactly(3);
    }

    @Test
    void SECTION_전략_헤딩없는_연속_페이지는_하나의_섹션으로_합쳐진다() {
        ExtractedDocument document = pdfDocument(List.of(
                new ExtractedSourceUnit(1, "Page 1", "첫 번째 페이지 본문이다."),
                new ExtractedSourceUnit(2, "Page 2", "두 번째 페이지 본문이다."),
                new ExtractedSourceUnit(3, "Page 3", "세 번째 페이지 본문이다.")
        ));

        List<ExtractedSection> sections = chunkingService.buildSections(document);

        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).sourceStartIndex()).isEqualTo(1);
        assertThat(sections.get(0).sourceEndIndex()).isEqualTo(3);
        assertThat(sections.get(0).sourceIndices()).containsExactly(1, 2, 3);
    }

    @Test
    void SECTION_전략_1600자_이하_섹션은_청크_1개() {
        // "운영체제 개요. " = 9 chars, * 100 = 900 chars → MAX_CHUNK_LENGTH(1600) 이하
        String content = "운영체제 개요. ".repeat(100);
        ExtractedDocument document = pdfDocument(List.of(
                new ExtractedSourceUnit(1, "Page 1", "1. 운영체제\n" + content)
        ));

        List<ExtractedSection> sections = chunkingService.buildSections(document);
        List<ExtractedChunk> chunks = chunkingService.chunk(document, sections, ChunkStrategy.SECTION);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).heading()).isEqualTo("1. 운영체제");
    }

    @Test
    void SECTION_전략_1600자_초과_섹션은_여러_청크로_분리되고_Part_헤딩이_붙는다() {
        // 각 문단 = "프로세스 스케줄링을 설명한다. "(17 chars) * 80 = 1360 chars
        // 두 문단 합계 ~2722 chars → MAX_CHUNK_LENGTH(1600) 초과 → 분리
        String paragraph1 = "프로세스 스케줄링을 설명한다. ".repeat(80);
        String paragraph2 = "메모리 관리 기법을 설명한다. ".repeat(80);
        ExtractedDocument document = pdfDocument(List.of(
                new ExtractedSourceUnit(1, "Page 1", "1. 운영체제\n" + paragraph1 + "\n\n" + paragraph2)
        ));

        List<ExtractedSection> sections = chunkingService.buildSections(document);
        List<ExtractedChunk> chunks = chunkingService.chunk(document, sections, ChunkStrategy.SECTION);

        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks.get(0).heading()).contains("1. 운영체제").contains("Part 1");
        assertThat(chunks.get(1).heading()).contains("1. 운영체제").contains("Part 2");
        // 분리된 청크들도 원본 섹션의 sourceIndex를 유지한다
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.sourceStartIndex()).isEqualTo(1);
            assertThat(chunk.sourceEndIndex()).isEqualTo(1);
        });
    }

    // ─── PARAGRAPH 전략 ──────────────────────────────────────────────────

    @Test
    void PARAGRAPH_전략_1100자_기준으로_SECTION보다_더_잘게_분리된다() {
        // 각 문단 = "프로세스 스케줄링을 설명한다. "(17 chars) * 36 = 612 chars
        // 두 문단 합계 ~1226 chars
        //   → SECTION  : MAX(1600) 이하라 청크 1개
        //   → PARAGRAPH: TARGET(1100) 초과라 문단 경계에서 분리되어 청크 2개
        String paragraph1 = "프로세스 스케줄링을 설명한다. ".repeat(36);
        String paragraph2 = "메모리 관리 기법을 설명한다. ".repeat(36);
        ExtractedDocument document = pdfDocument(List.of(
                new ExtractedSourceUnit(1, "Page 1", "1. 운영체제\n" + paragraph1 + "\n\n" + paragraph2)
        ));

        List<ExtractedSection> sections = chunkingService.buildSections(document);
        List<ExtractedChunk> sectionChunks = chunkingService.chunk(document, sections, ChunkStrategy.SECTION);
        List<ExtractedChunk> paragraphChunks = chunkingService.chunk(document, sections, ChunkStrategy.PARAGRAPH);

        assertThat(sectionChunks).hasSize(1);
        assertThat(paragraphChunks.size()).isGreaterThan(sectionChunks.size());
    }

    // ─── PAGE 전략 ───────────────────────────────────────────────────────

    @Test
    void PAGE_전략_각_비어있지_않은_페이지가_독립_청크가_된다() {
        ExtractedDocument document = pdfDocument(List.of(
                new ExtractedSourceUnit(1, "Page 1", "1장 내용"),
                new ExtractedSourceUnit(2, "Page 2", "2장 내용"),
                new ExtractedSourceUnit(3, "Page 3", "")  // blank → 제외
        ));

        List<ExtractedSection> sections = chunkingService.buildSections(document);
        List<ExtractedChunk> chunks = chunkingService.chunk(document, sections, ChunkStrategy.PAGE);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).sourceStartIndex()).isEqualTo(1);
        assertThat(chunks.get(0).sourceEndIndex()).isEqualTo(1);
        assertThat(chunks.get(0).sourceIndices()).containsExactly(1);
        assertThat(chunks.get(1).sourceStartIndex()).isEqualTo(2);
        assertThat(chunks.get(1).sourceEndIndex()).isEqualTo(2);
        assertThat(chunks.get(1).sourceIndices()).containsExactly(2);
    }

    @Test
    void PAGE_전략을_슬라이드_문서에_적용하면_예외() {
        assertThatThrownBy(() -> chunkingService.resolveStrategy(SourceUnitType.SLIDE, ChunkStrategy.PAGE))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.PAGE_CHUNK_STRATEGY_NOT_ALLOWED.getMessage());
    }

    // ─── SLIDE 전략 ──────────────────────────────────────────────────────

    @Test
    void SLIDE_전략_각_비어있지_않은_슬라이드가_독립_청크가_된다() {
        ExtractedDocument document = pptxDocument(List.of(
                new ExtractedSourceUnit(1, "슬라이드 1 제목", "첫 번째 슬라이드 본문"),
                new ExtractedSourceUnit(2, "슬라이드 2 제목", "두 번째 슬라이드 본문"),
                new ExtractedSourceUnit(3, "슬라이드 3 제목", "   ")  // blank → 제외
        ));

        List<ExtractedSection> sections = chunkingService.buildSections(document);
        List<ExtractedChunk> chunks = chunkingService.chunk(document, sections, ChunkStrategy.SLIDE);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).sourceType()).isEqualTo(SourceUnitType.SLIDE);
        assertThat(chunks.get(0).sourceStartIndex()).isEqualTo(1);
        assertThat(chunks.get(1).sourceStartIndex()).isEqualTo(2);
    }

    @Test
    void SLIDE_전략을_PDF_문서에_적용하면_예외() {
        assertThatThrownBy(() -> chunkingService.resolveStrategy(SourceUnitType.PAGE, ChunkStrategy.SLIDE))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.SLIDE_CHUNK_STRATEGY_NOT_ALLOWED.getMessage());
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────

    private ExtractedDocument pdfDocument(List<ExtractedSourceUnit> sourceUnits) {
        return new ExtractedDocument("lecture.pdf", "application/pdf", SourceUnitType.PAGE, sourceUnits);
    }

    private ExtractedDocument pptxDocument(List<ExtractedSourceUnit> sourceUnits) {
        return new ExtractedDocument(
                "lecture.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                SourceUnitType.SLIDE,
                sourceUnits
        );
    }
}
