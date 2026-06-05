package org.example.snow.document.application;

import lombok.RequiredArgsConstructor;
import org.example.snow.document.application.chunking.BlockSplitter;
import org.example.snow.document.application.chunking.BoilerplateFilter;
import org.example.snow.document.application.chunking.TextBlock;
import org.example.snow.document.application.port.TextExtractor;
import org.example.snow.document.domain.ExtractedDocument;
import org.example.snow.document.domain.ExtractedSourceUnit;
import org.example.snow.global.exception.BusinessException;
import org.example.snow.global.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 문서 ingest Phase A — 임베딩에 무관한 순수 텍스트 처리.
 *
 * 추출 → 전처리(NUL/wrapping 등) → 반복 머리말/꼬리말 제거 → 문단 블록 분리까지 수행한다.
 * Section 구성은 임베딩 유사도가 필요하므로 여기서 하지 않고 DocumentAnalysisService가 이어받는다.
 */
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final List<TextExtractor> textExtractors;
    private final TextPreprocessor textPreprocessor;
    private final BoilerplateFilter boilerplateFilter;
    private final BlockSplitter blockSplitter;

    public IngestedDocument ingest(DocumentUploadCommand command) {
        UploadedDocument file = validateFile(command);
        TextExtractor extractor = resolveExtractor(file);
        ExtractedDocument extractedDocument = extractor.extract(file);
        ExtractedDocument preprocessedDocument = preprocess(extractedDocument);

        // 반복 머리말/꼬리말·페이지 번호 제거 (블록 경계 오염 방지)
        BoilerplateFilter.Result filtered = boilerplateFilter.filter(preprocessedDocument.sourceUnits());
        ExtractedDocument cleanedDocument = preprocessedDocument.withSourceUnits(filtered.units());

        List<TextBlock> blocks = blockSplitter.split(cleanedDocument);

        return new IngestedDocument(cleanedDocument, filtered.repeatedLines(), blocks);
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
}
