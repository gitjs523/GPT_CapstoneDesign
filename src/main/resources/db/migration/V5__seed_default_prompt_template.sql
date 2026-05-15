-- V5: 기본 퀴즈 생성 prompt_template seed
-- active 템플릿이 이미 있으면 운영자가 넣은 값을 우선하고, 없을 때만 기본 템플릿을 활성화한다.

INSERT INTO prompt_template (
    prompt_version,
    system_prompt,
    user_prompt_template,
    output_schema,
    is_active,
    created_at
)
SELECT
    'quiz-generation-v1',
    $system_prompt$
너는 강의자료 기반 학습 퀴즈 생성 AI다.
반드시 제공된 검색 문맥만 근거로 문제를 생성해라.
답변은 한국어로 작성해라.
반드시 JSON 객체 하나만 반환해라.
마크다운, 코드 블록, 설명 문장, 추가 텍스트는 금지한다.
$system_prompt$,
    $user_prompt_template$
생성 요청:
- 범위: {scopeText}
- 문제 유형: {quizType}
- 난이도: {difficulty}
- 현재 문제 번호: {quizOrder}

검색된 강의자료:
{contextSections}

출력 스키마:
{outputSchema}

규칙:
- 위 강의자료만 근거로 문제 1개를 생성한다.
- 객관식이면 choices는 JSON 배열 문자열로 작성한다.
- 주관식이면 choices는 빈 문자열로 둔다.
- sourceSectionIds에는 실제 근거로 사용한 sectionId만 숫자 배열로 넣는다.
$user_prompt_template$,
    $output_schema$
{
  "quizType": "string",
  "questionText": "string",
  "choices": ["string"],
  "answer": "string",
  "explanation": "string",
  "sourceSectionIds": [1]
}
$output_schema$,
    TRUE,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM prompt_template
    WHERE is_active IS TRUE
)
ON CONFLICT (prompt_version) DO NOTHING;

UPDATE prompt_template
SET is_active = TRUE
WHERE prompt_version = 'quiz-generation-v1'
  AND NOT EXISTS (
      SELECT 1
      FROM prompt_template
      WHERE is_active IS TRUE
  );
