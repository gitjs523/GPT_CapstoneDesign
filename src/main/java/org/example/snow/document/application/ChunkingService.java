package org.example.snow.document.application;

import lombok.RequiredArgsConstructor;
import org.example.snow.document.application.chunking.ChunkComposer;
import org.example.snow.document.application.chunking.SectionBuilder;
import org.example.snow.document.domain.ExtractedChunk;
import org.example.snow.document.domain.ExtractedDocument;
import org.example.snow.document.domain.ExtractedSection;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChunkingService {

    private final SectionBuilder sectionBuilder;
    private final ChunkComposer chunkComposer;

    public List<ExtractedSection> buildSections(ExtractedDocument document) {
        return sectionBuilder.build(document);
    }

    public List<ExtractedChunk> chunk(List<ExtractedSection> sections) {
        return chunkComposer.compose(sections);
    }
}
