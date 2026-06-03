-- V6: 문서 대표 제목(docTitle) 컬럼 추가
-- 임베딩 contextual header의 [문서: ...] 및 UI 표시에 사용한다.
-- 반복 머리말 / 첫 섹션 헤딩 / 정리된 파일명 순으로 분석 시점에 결정된다.

ALTER TABLE document ADD COLUMN title VARCHAR(255);
