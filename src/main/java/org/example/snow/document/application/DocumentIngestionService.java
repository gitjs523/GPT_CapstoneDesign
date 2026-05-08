package org.example.snow.document.application;

import lombok.RequiredArgsConstructor;
import org.example.snow.document.application.port.TextExtractor;
import org.example.snow.document.domain.ChunkStrategy;
import org.example.snow.document.domain.ExtractedChunk;
import org.example.snow.document.domain.ExtractedDocument;
import org.example.snow.document.domain.ExtractedSection;
import org.example.snow.document.domain.ExtractedSourceUnit;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final List<TextExtractor> textExtractors;
    private final TextPreprocessor textPreprocessor;
    private final ChunkingService chunkingService;

    public DocumentProcessingResult ingest(DocumentUploadCommand command) {
        UploadedDocument file = validateFile(command);
        TextExtractor extractor = resolveExtractor(file);
        ExtractedDocument extractedDocument = extractor.extract(file);
        ExtractedDocument preprocessedDocument = preprocess(extractedDocument);
        ChunkStrategy appliedStrategy = chunkingService.resolveStrategy(
                preprocessedDocument.sourceType(),
                command.chunkStrategy()
        );
        List<ExtractedSection> sections = chunkingService.buildSections(preprocessedDocument);
        List<ExtractedChunk> chunks = chunkingService.chunk(preprocessedDocument, sections, appliedStrategy);
        String preprocessedText = joinSourceUnits(preprocessedDocument.sourceUnits());

        return new DocumentProcessingResult(
                preprocessedDocument.originalFilename(),
                preprocessedDocument.contentType(),
                appliedStrategy,
                preprocessedDocument.sourceUnits().size(),
                sections.size(),
                chunks.size(),
                preprocessedText.length(),
                preprocessedText,
                preprocessedDocument,
                sections,
                chunks
        );
    }

    private UploadedDocument validateFile(DocumentUploadCommand command) {
        if (command == null || command.file() == null || command.file().isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_REQUIRED);
        }
        return command.file();
    }

    private TextExtractor resolveExtractor(UploadedDocument file) {
        return textExtractors.stream()
                .filter(extractor -> extractor.supports(file))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_DOCUMENT_TYPE));
    }

    private ExtractedDocument preprocess(ExtractedDocument document) {
        try {
            List<ExtractedSourceUnit> sourceUnits = document.sourceUnits().stream()
                    .map(sourceUnit -> new ExtractedSourceUnit(
                            sourceUnit.index(),
                            sourceUnit.heading(),
                            textPreprocessor.normalize(sourceUnit.text())
                    ))
                    .toList();
            return document.withSourceUnits(sourceUnits);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.DOCUMENT_PREPROCESSING_FAILED, exception);
        }
    }

    private String joinSourceUnits(List<ExtractedSourceUnit> sourceUnits) {
        return sourceUnits.stream()
                .map(ExtractedSourceUnit::text)
                .filter(text -> !text.isBlank())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }
}
