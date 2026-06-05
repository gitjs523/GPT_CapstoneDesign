package org.example.snow.document.application;

import org.example.snow.document.application.chunking.TextBlock;
import org.example.snow.document.domain.ExtractedDocument;

import java.util.List;

/**
 * 문서 ingest(Phase A: 추출→전처리→boilerplate 제거→블록 분리) 결과.
 *
 * Section은 임베딩이 필요하므로 여기서 만들지 않는다 — 블록까지만 만들고,
 * 임베딩·semantic 분할은 DocumentAnalysisService가 이어서 수행한다.
 */
public record IngestedDocument(
        ExtractedDocument extractedDocument,
        List<String> repeatedLines,
        List<TextBlock> blocks
) {
}
