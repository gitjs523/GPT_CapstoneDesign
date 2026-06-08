-- V8: Force quiz generation output text to Korean regardless of source document language.

UPDATE prompt_template
SET is_active = FALSE
WHERE is_active IS TRUE;

INSERT INTO prompt_template (
    prompt_version,
    system_prompt,
    user_prompt_template,
    output_schema,
    is_active,
    created_at
)
VALUES (
    'quiz-generation-v2-korean-output',
    $system_prompt$
너는 강의자료 기반 학습 퀴즈 생성 AI다.
반드시 제공된 검색 문맥만 근거로 문제를 생성해라.
강의자료가 영어 또는 다른 언어로 되어 있어도, 생성하는 퀴즈의 모든 사용자 표시 텍스트는 반드시 한국어로 작성해라.
JSON 필드명은 영어로 유지하되, questionText, choices, answer, explanation 값은 한국어로 작성해라.
중요한 전공 용어는 필요한 경우 한국어 번역 뒤에 영어 원어를 괄호로 병기해라.
원문을 그대로 영어로 복사하지 말고, 의미를 유지한 자연스러운 한국어 학습 문장으로 바꿔라.
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
- 이 강의자료만 근거로 문제 1개를 생성한다.
- questionText, choices, answer, explanation 값은 반드시 한국어로 작성한다.
- 강의자료 원문이 영어이면, 내용을 이해한 뒤 한국어 문제로 번역/재구성한다.
- 중요한 전공 용어는 필요한 경우 한국어 번역 뒤에 영어 원어를 괄호로 병기한다.
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
)
ON CONFLICT (prompt_version) DO UPDATE
SET system_prompt = EXCLUDED.system_prompt,
    user_prompt_template = EXCLUDED.user_prompt_template,
    output_schema = EXCLUDED.output_schema,
    is_active = TRUE;