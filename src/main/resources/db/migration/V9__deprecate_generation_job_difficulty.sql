-- V9: 난이도(difficulty) 입력 제거 (function-specs #31)
-- 난이도는 외부에서 제어할 수단이 없고 소형 생성 모델에 난이도 판단을 위임하는 것이
-- 본 프로젝트 방향과 맞지 않다고 판단해 입력·프롬프트에서 제거한다.
-- 컬럼은 되돌리기 용이성을 위해 nullable로 보류하며, 신규 row에는 기록하지 않는다(완전 제거는 후속 판단).

ALTER TABLE generation_job
    ALTER COLUMN difficulty DROP NOT NULL;

-- 모든 퀴즈 생성 템플릿에서 {difficulty} placeholder 줄을 제거한다.
-- 코드에서 {difficulty} 치환을 더 이상 수행하지 않으므로, 남겨두면 프롬프트에 리터럴로 새어나간다.
-- V8(quiz-generation-v2-korean-output)이 활성 템플릿을 교체하면서 {difficulty} 줄을 다시
-- 포함했으므로, 특정 버전이 아니라 placeholder가 남은 모든 템플릿을 대상으로 줄 단위로 제거한다.
UPDATE prompt_template
SET user_prompt_template = replace(user_prompt_template, E'- 난이도: {difficulty}\n', '')
WHERE user_prompt_template LIKE '%{difficulty}%';
