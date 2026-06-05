package org.example.snow.document.application.chunking;

import org.example.snow.document.domain.SourceUnitType;

/**
 * 문단 단위 텍스트 블록. semantic 분할의 임베딩/검색 최소 단위이자 최종 Chunk가 된다.
 * page는 원본 SourceUnit 인덱스(PDF 페이지/PPT 슬라이드 번호)로 원문 추적에 쓰인다.
 */
public record TextBlock(int page, String text, SourceUnitType sourceType) {
}
