package org.example.snow.document.application;

import org.example.snow.document.application.chunking.ChunkComposer;
import org.example.snow.document.application.chunking.ChunkStrategyResolver;
import org.example.snow.document.application.chunking.SectionBuilder;
import org.example.snow.document.domain.Chunk;
import org.example.snow.document.domain.ChunkStrategy;
import org.example.snow.document.domain.ExtractedDocument;
import org.example.snow.document.domain.Section;
import org.example.snow.document.domain.SourceUnit;
import org.example.snow.document.domain.SourceUnitType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkingServiceTest {

    private final ChunkingService chunkingService = new ChunkingService(
            new ChunkStrategyResolver(),
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
                        new SourceUnit(
                                1,
                                "Page 1",
                                "1. RAG Overview\nRAG는 검색 증강 생성이다.\n\n검색 단계가 필요하다."
                        ),
                        new SourceUnit(
                                2,
                                "Page 2",
                                "임베딩은 문서를 벡터로 변환한다.\n\n유사도 검색에 사용된다."
                        ),
                        new SourceUnit(
                                3,
                                "Page 3",
                                "2. Embedding Pipeline\n청킹 후 임베딩을 생성한다."
                        )
                )
        );

        List<Section> sections = chunkingService.buildSections(document);

        assertThat(sections).hasSize(2);
        assertThat(sections.get(0).heading()).isEqualTo("1. RAG Overview");
        assertThat(sections.get(0).sourceStartIndex()).isEqualTo(1);
        assertThat(sections.get(0).sourceEndIndex()).isEqualTo(2);
        assertThat(sections.get(0).sourceIndices()).containsExactly(1, 2);
        assertThat(sections.get(1).heading()).isEqualTo("2. Embedding Pipeline");
        assertThat(sections.get(1).sourceIndices()).containsExactly(3);
    }

    @Test
    void splitsLongSectionsAndKeepsPhysicalMetadata() {
        String paragraphOne = "RAG 개요를 설명한다. ".repeat(120);
        String paragraphTwo = "임베딩 생성 과정을 설명한다. ".repeat(120);
        String paragraphThree = "검색 단계의 상세 동작을 설명한다. ".repeat(120);

        ExtractedDocument document = new ExtractedDocument(
                "guide.pdf",
                "application/pdf",
                SourceUnitType.PAGE,
                List.of(
                        new SourceUnit(
                                1,
                                "Page 1",
                                "1. RAG Overview\n" + paragraphOne + "\n\n" + paragraphTwo
                        ),
                        new SourceUnit(
                                2,
                                "Page 2",
                                paragraphThree
                        )
                )
        );

        List<Section> sections = chunkingService.buildSections(document);
        List<Chunk> chunks = chunkingService.chunk(document, sections, ChunkStrategy.SECTION);

        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.sourceType()).isEqualTo(SourceUnitType.PAGE);
            assertThat(chunk.sourceStartIndex()).isEqualTo(1);
            assertThat(chunk.sourceEndIndex()).isEqualTo(2);
            assertThat(chunk.sourceIndices()).containsExactly(1, 2);
        });
        assertThat(chunks.get(0).heading()).contains("RAG Overview");
    }
}
