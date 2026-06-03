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

class ChunkingServiceTest {

    private final ChunkingService chunkingService = new ChunkingService(
            new SectionBuilder(),
            new ChunkComposer()
    );

    @Test
    void buildsSectionsAcrossPhysicalPages() {
        ExtractedDocument document = new ExtractedDocument(
                "guide.pdf",
                "application/pdf",
                SourceUnitType.PAGE,
                List.of(
                        new ExtractedSourceUnit(
                                1,
                                "Page 1",
                                "1. RAG Overview\nRAG는 검색 증강 생성이며 외부 문서를 근거로 답을 만든다.\n\n검색 단계가 반드시 필요하다."
                        ),
                        new ExtractedSourceUnit(
                                2,
                                "Page 2",
                                "임베딩은 문서를 벡터로 변환하여 유사도 검색을 가능하게 하는 핵심 단계이다."
                        ),
                        new ExtractedSourceUnit(
                                3,
                                "Page 3",
                                "2. Embedding Pipeline\n청킹 후 각 조각에 대해 임베딩 벡터를 생성하고 저장한다."
                        )
                )
        );

        List<ExtractedSection> sections = chunkingService.buildSections(document);

        assertThat(sections).hasSize(2);
        assertThat(sections.get(0).heading()).isEqualTo("1. RAG Overview");
        assertThat(sections.get(0).sourceStartIndex()).isEqualTo(1);
        assertThat(sections.get(0).sourceEndIndex()).isEqualTo(2);
        assertThat(sections.get(0).sourceIndices()).containsExactly(1, 2);
        assertThat(sections.get(1).heading()).isEqualTo("2. Embedding Pipeline");
        assertThat(sections.get(1).sourceIndices()).containsExactly(3);
    }

    @Test
    void splitsLongSectionIntoMultipleChunksKeepingMetadata() {
        // 한 페이지에 긴 본문 → Section 1개가 여러 Chunk(~400자)로 분리된다
        String body = "RAG 개요와 동작 원리를 자세히 설명한다. ".repeat(80);

        ExtractedDocument document = new ExtractedDocument(
                "guide.pdf",
                "application/pdf",
                SourceUnitType.PAGE,
                List.of(new ExtractedSourceUnit(1, "Page 1", "1. RAG Overview\n" + body))
        );

        List<ExtractedSection> sections = chunkingService.buildSections(document);
        List<ExtractedChunk> chunks = chunkingService.chunk(sections);

        assertThat(sections).hasSize(1);
        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.sourceType()).isEqualTo(SourceUnitType.PAGE);
            assertThat(chunk.sourceStartIndex()).isEqualTo(1);
            assertThat(chunk.sourceEndIndex()).isEqualTo(1);
            assertThat(chunk.sourceIndices()).containsExactly(1);
        });
        assertThat(chunks.get(0).heading()).contains("RAG Overview");
    }
}
