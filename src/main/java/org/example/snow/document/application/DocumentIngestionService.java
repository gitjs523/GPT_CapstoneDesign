package org.example.snow.document.application;

import lombok.RequiredArgsConstructor;
import org.example.snow.document.application.chunking.BoilerplateFilter;
import org.example.snow.document.application.chunking.DocumentTitleResolver;
import org.example.snow.document.application.port.TextExtractor;
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
    private final BoilerplateFilter boilerplateFilter;
    private final DocumentTitleResolver documentTitleResolver;

    public DocumentProcessingResult ingest(DocumentUploadCommand command) {
        UploadedDocument file = validateFile(command);
        TextExtractor extractor = resolveExtractor(file);
        ExtractedDocument extractedDocument = extractor.extract(file);
        ExtractedDocument preprocessedDocument = preprocess(extractedDocument);

        // 반복 머리말/꼬리말·페이지 번호 제거 (Section 경계 오염 방지)
        BoilerplateFilter.Result filtered = boilerplateFilter.filter(preprocessedDocument.sourceUnits());
        ExtractedDocument cleanedDocument = preprocessedDocument.withSourceUnits(filtered.units());

        List<ExtractedSection> sections = chunkingService.buildSections(cleanedDocument);
        List<ExtractedChunk> chunks = chunkingService.chunk(sections);
        String preprocessedText = joinSourceUnits(cleanedDocument.sourceUnits());
        String docTitle = documentTitleResolver.resolve(
                filtered.repeatedLines(), sections, cleanedDocument.originalFilename());

        return new DocumentProcessingResult(
                cleanedDocument.originalFilename(),
                cleanedDocument.contentType(),
                docTitle,
                cleanedDocument.sourceUnits().size(),
                sections.size(),
                chunks.size(),
                preprocessedText.length(),
                preprocessedText,
                cleanedDocument,
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
