package org.example.snow.document.application;

import org.example.snow.document.application.chunking.ChunkComposer;
import org.example.snow.document.application.chunking.SectionBuilder;
import org.example.snow.document.domain.ExtractedChunk;
import org.example.snow.document.domain.ExtractedDocument;
import org.example.snow.document.domain.ExtractedSection;
import org.example.snow.document.domain.ExtractedSourceUnit;
import org.example.snow.document.domain.SourceUnitType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SourceUnit → Section → Chunk 파이프라인 검증.
 *
 * 단일 통합 전략(전처리 후 헤딩 기반 Section + 크기 가드, ~400자 Chunk + 오버랩)을 검증한다.
 *
 * 크기 기준:
 *   Section MAX_SECTION_CHARS = 1,200 (초과 시 페이지 경계 분할)
 *   Section MIN_SECTION_CHARS = 30 (미만 시 인접 Section으로 흡수)
 *   Chunk   TARGET_CHUNK_CHARS = 400 (초과 시 분리, Part 헤딩 부여)
 */
class ChunkingPipelineTest {

    private final ChunkingService chunkingService = new ChunkingService(
            new SectionBuilder(),
            new ChunkComposer()
    );

    private ExtractedDocument pdfDocument(List<ExtractedSourceUnit> sourceUnits) {
        return new ExtractedDocument("lecture.pdf", "application/pdf", SourceUnitType.PAGE, sourceUnits);
    }

    // ─── 빈 문서 ────────────────────────────────────────────────────────

    @Test
    void 전체_blank_문서는_섹션과_청크가_0개() {
        ExtractedDocument document = pdfDocument(List.of(
                new ExtractedSourceUnit(1, "Page 1", ""),
                new ExtractedSourceUnit(2, "Page 2", "   "),
                new ExtractedSourceUnit(3, "Page 3", "\n\n")
        ));

        List<ExtractedSection> sections = chunkingService.buildSections(document);
        List<ExtractedChunk> chunks = chunkingService.chunk(sections);

        assertThat(sections).isEmpty();
        assertThat(chunks).isEmpty();
    }

    @Test
    void 일부_blank_페이지는_내용있는_페이지로만_섹션이_생성된다() {
        ExtractedDocument document = pdfDocument(List.of(
                new ExtractedSourceUnit(1, "Page 1", ""),
                new ExtractedSourceUnit(2, "Page 2", "운영체제는 하드웨어와 소프트웨어 사이의 인터페이스 역할을 하는 기반 소프트웨어다."),
                new ExtractedSourceUnit(3, "Page 3", "   ")
        ));

        List<ExtractedSection> sections = chunkingService.buildSections(document);

        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).sourceIndices()).containsExactly(2);
    }

    // ─── Section 경계 ───────────────────────────────────────────────────

    @Test
    void 헤딩없는_단일_페이지는_섹션_1개_청크_1개() {
        ExtractedDocument document = pdfDocument(List.of(
                new ExtractedSourceUnit(1, "Page 1", "운영체제는 하드웨어와 소프트웨어 사이의 인터페이스 역할을 하는 기반 소프트웨어다.")
        ));

        List<ExtractedSection> sections = chunkingService.buildSections(document);
        List<ExtractedChunk> chunks = chunkingService.chunk(sections);

        assertThat(sections).hasSize(1);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).contains("운영체제는");
        assertThat(chunks.get(0).sourceStartIndex()).isEqualTo(1);
        assertThat(chunks.get(0).sourceEndIndex()).isEqualTo(1);
    }

    @Test
    void 헤딩이_달라지면_새_섹션이_시작된다() {
        ExtractedDocument document = pdfDocument(List.of(
                new ExtractedSourceUnit(1, "Page 1",
                        "1. 프로세스 관리\n프로세스는 실행 중인 프로그램이며 운영체제가 자원을 할당하는 기본 단위이다."),
                new ExtractedSourceUnit(2, "Page 2",
                        "프로세스 스케줄링은 CPU를 여러 프로세스에 효율적으로 할당하기 위한 과정이다."),
                new ExtractedSourceUnit(3, "Page 3",
                        "2. 메모리 관리\n메모리는 프로세스의 요청에 따라 동적으로 할당되고 회수되는 핵심 자원이다.")
        ));

        List<ExtractedSection> sections = chunkingService.buildSections(document);

        assertThat(sections).hasSize(2);
        assertThat(sections.get(0).heading()).isEqualTo("1. 프로세스 관리");
        assertThat(sections.get(0).sourceIndices()).containsExactly(1, 2);
        assertThat(sections.get(1).heading()).isEqualTo("2. 메모리 관리");
        assertThat(sections.get(1).sourceIndices()).containsExactly(3);
    }

    @Test
    void 헤딩없는_연속_페이지는_하나의_섹션으로_합쳐진다() {
        ExtractedDocument document = pdfDocument(List.of(
                new ExtractedSourceUnit(1, "Page 1", "첫 번째 페이지의 본문 내용이 충분히 길게 담겨 있다."),
                new ExtractedSourceUnit(2, "Page 2", "두 번째 페이지의 본문 내용도 이어서 작성되어 있다."),
                new ExtractedSourceUnit(3, "Page 3", "세 번째 페이지의 본문 내용으로 마무리된다.")
        ));

        List<ExtractedSection> sections = chunkingService.buildSections(document);

        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).sourceIndices()).containsExactly(1, 2, 3);
    }

    // ─── tiny Section 흡수 ───────────────────────────────────────────────

    @Test
    void 짧은_구분_슬라이드는_다음_섹션으로_흡수된다() {
        ExtractedDocument document = pdfDocument(List.of(
                new ExtractedSourceUnit(1, "Page 1", "03. 계정 관리"),
                new ExtractedSourceUnit(2, "Page 2",
                        "AWS IAM\nIAM은 액세스를 안전하게 관리하는 글로벌 서비스이며 사용자와 권한을 제어한다.")
        ));

        List<ExtractedSection> sections = chunkingService.buildSections(document);

        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).sourceIndices()).containsExactly(1, 2);
    }

    @Test
    void 마지막_짧은_섹션은_이전_섹션으로_흡수된다() {
        ExtractedDocument document = pdfDocument(List.of(
                new ExtractedSourceUnit(1, "Page 1",
                        "1. 개요\n이 단원은 클라우드 컴퓨팅의 기본 개념과 다양한 활용 사례를 폭넓게 다룬다."),
                new ExtractedSourceUnit(2, "Page 2", "2. 끝")
        ));

        List<ExtractedSection> sections = chunkingService.buildSections(document);

        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).heading()).isEqualTo("1. 개요");
        assertThat(sections.get(0).sourceIndices()).containsExactly(1, 2);
    }

    @Test
    void 긴_불릿_문장은_헤딩으로_쓰이지_않는다() {
        // 이전 페이지에서 넘어온 긴 불릿 문장이 헤딩이 되면 안 된다 (P1)
        ExtractedDocument document = pdfDocument(List.of(
                new ExtractedSourceUnit(1, "Page 1",
                        "1. 개요\n클라우드 컴퓨팅의 기본 개념을 충분히 길게 설명하는 본문 문장이 여기에 들어간다."),
                new ExtractedSourceUnit(2, "Page 2",
                        "· 이것은 마흔 글자를 넘기는 아주 긴 불릿 문장이라서 헤딩으로 쓰이면 안 되고 본문에 머물러야 한다 정말로")
        ));

        List<ExtractedSection> sections = chunkingService.buildSections(document);

        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).heading()).isEqualTo("1. 개요");
        assertThat(sections.get(0).sourceIndices()).containsExactly(1, 2);
    }

    @Test
    void 짧은_불릿_제목은_헤딩으로_인정된다() {
        ExtractedDocument document = pdfDocument(List.of(
                new ExtractedSourceUnit(1, "Page 1",
                        "➢ Root 사용자\n계정을 생성할 때 같이 생성되며 모든 권한을 가지는 특별한 사용자이다.")
        ));

        List<ExtractedSection> sections = chunkingService.buildSections(document);

        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).heading()).isEqualTo("➢ Root 사용자");
    }

    // ─── Chunk 분리 ─────────────────────────────────────────────────────

    @Test
    void 목표_크기_이하_섹션은_청크_1개() {
        String body = "운영체제 개요. ".repeat(20); // ~180자 < 400
        ExtractedDocument document = pdfDocument(List.of(
                new ExtractedSourceUnit(1, "Page 1", "1. 운영체제\n" + body)
        ));

        List<ExtractedSection> sections = chunkingService.buildSections(document);
        List<ExtractedChunk> chunks = chunkingService.chunk(sections);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).heading()).isEqualTo("1. 운영체제");
    }

    @Test
    void 목표_크기_초과_섹션은_여러_청크로_분리되고_Part_헤딩이_붙는다() {
        String body = "프로세스 스케줄링의 개념과 동작 방식을 설명한다. ".repeat(40); // ~1000자 > 400, < 1200
        ExtractedDocument document = pdfDocument(List.of(
                new ExtractedSourceUnit(1, "Page 1", "1. 운영체제\n" + body)
        ));

        List<ExtractedSection> sections = chunkingService.buildSections(document);
        List<ExtractedChunk> chunks = chunkingService.chunk(sections);

        assertThat(sections).hasSize(1);
        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks.get(0).heading()).contains("1. 운영체제").contains("Part 1");
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.sourceStartIndex()).isEqualTo(1);
            assertThat(chunk.sourceEndIndex()).isEqualTo(1);
        });
    }
}
