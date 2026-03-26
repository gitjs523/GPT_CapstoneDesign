package org.example.snow.document.application;

import lombok.RequiredArgsConstructor;
import org.example.snow.document.application.chunking.ChunkComposer;
import org.example.snow.document.application.chunking.ChunkStrategyResolver;
import org.example.snow.document.application.chunking.SectionBuilder;
import org.example.snow.document.domain.Chunk;
import org.example.snow.document.domain.ChunkStrategy;
import org.example.snow.document.domain.ExtractedDocument;
import org.example.snow.document.domain.Section;
import org.example.snow.document.domain.SourceUnitType;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChunkingService {

    private final ChunkStrategyResolver chunkStrategyResolver;
    private final SectionBuilder sectionBuilder;
    private final ChunkComposer chunkComposer;

    public ChunkStrategy resolveStrategy(SourceUnitType sourceType, ChunkStrategy requestedStrategy) {
        return chunkStrategyResolver.resolve(sourceType, requestedStrategy);
    }

    public List<Section> buildSections(ExtractedDocument document) {
        return sectionBuilder.build(document);
    }

    public List<Chunk> chunk(
            ExtractedDocument document,
            List<Section> sections,
            ChunkStrategy appliedStrategy
    ) {
        return chunkComposer.compose(document, sections, appliedStrategy);
    }
}
