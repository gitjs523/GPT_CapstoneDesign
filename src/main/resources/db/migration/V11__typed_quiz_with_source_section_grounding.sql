-- V11: 문제 유형 3분기(객관식/단답형/서술형) + sourceSectionIds 근거 엄격화 통합 프롬프트.
-- V10(source-sections)의 validSectionIds 주입·근거 규칙을 유지하고, 약한 2분기(객관식/주관식) 규칙을
-- 3분기(객관식/단답형/서술형)로 교체한다. V8 한국어 출력 강제·V9 difficulty 제거 정책 유지.
-- 프롬프트 변경 추적(generation_job.prompt_version)을 위해 in-place 수정이 아니라 신규 버전으로 추가한다.

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
    'quiz-generation-v4-typed-grounded',
    $system_prompt$
너는 강의자료 기반 학습 퀴즈 생성 AI다.
반드시 제공된 검색 문맥만 근거로 문제를 생성해라.
강의자료가 영어 또는 다른 언어로 되어 있어도, 생성하는 퀴즈의 모든 사용자 표시 텍스트는 반드시 한국어로 작성해라.
JSON 필드명은 영어로 유지하되, questionText, choices, answer, explanation 값은 한국어로 작성해라.
중요한 원문 용어가 필요한 경우 한국어 번역 뒤에 원어를 괄호로 병기해라.
원문을 그대로 영어로 복사하지 말고, 의미를 유지하는 자연스러운 한국어 학습 문장으로 바꿔라.
반드시 JSON 객체 하나만 반환해라.
마크다운, 코드 블록, 설명 문장, 추가 텍스트는 금지한다.
$system_prompt$,
    $user_prompt_template$
생성 요청:
- 범위: {scopeText}
- 문제 유형: {quizType}
- 현재 문제 번호: {quizOrder}

검색된 강의자료:
{contextSections}

유효한 sectionId 목록:
{validSectionIds}

출력 스키마:
{outputSchema}

규칙:
- 위 강의자료만 근거로 문제 1개를 생성한다.
- questionText, choices, answer, explanation 값은 반드시 한국어로 작성한다.
- 강의자료 원문이 영어이면, 내용을 이해한 뒤 한국어 문제로 번역/재구성한다.
- 중요한 원문 용어가 필요한 경우 한국어 번역 뒤에 원어를 괄호로 병기한다.
- 객관식이면 choices에 4개 내외의 보기를 JSON 배열 문자열로 작성하고, answer는 보기 중 정답 하나로 둔다.
- 단답형이면 choices는 빈 문자열로 두고, answer는 핵심 정답을 단어 또는 짧은 구로 간결하게 작성한다.
- 서술형이면 choices는 빈 문자열로 두고, answer는 핵심 내용을 2~3문장으로 설명하듯 서술한다.
- sourceSectionIds에는 반드시 위 "유효한 sectionId 목록"에 있는 숫자만 넣는다.
- sourceSectionIds에는 문제 생성에 실제로 사용한 sectionId를 1개 이상 넣는다.
- 컨텍스트에 없는 sectionId, 예시 숫자, 임의 숫자는 절대 넣지 않는다.
$user_prompt_template$,
    $output_schema$
{
  "quizType": "string",
  "questionText": "string",
  "choices": ["string"],
  "answer": "string",
  "explanation": "string",
  "sourceSectionIds": []
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
