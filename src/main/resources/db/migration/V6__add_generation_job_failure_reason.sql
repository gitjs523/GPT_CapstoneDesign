-- V6: generation_job 실패 사유 컬럼 추가
-- 공유 RDS에 선적용되었으나 어떤 브랜치에도 커밋되지 않았던 마이그레이션을 이력 정합성을 위해 복원한다.
-- 이미 적용된 환경을 위해 IF NOT EXISTS로 멱등 처리한다.

ALTER TABLE generation_job ADD COLUMN IF NOT EXISTS failure_reason TEXT;
