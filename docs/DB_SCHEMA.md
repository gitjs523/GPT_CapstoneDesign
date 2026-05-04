# DB 스키마

SNOW 프로젝트의 기준 DB 스키마 문서다.
기능 명세서(`function-specs.md`)가 기준이며, Flyway 마이그레이션 파일은 이 문서를 따른다.

---

## 용어 정의

| 용어 | 의미 |
|---|---|
| `quiz` | 문제 (AI가 생성한 시험 문제) |
| `question` / `qa` | 질문 (사용자가 입력하는 Q&A 질문, `qa`는 축약형) |

---

## 테이블 목록

| 테이블 | 역할 |
|---|---|
| `users` | 사용자 계정 |
| `notebook` | 학습 단위 공간 |
| `document` | 노트북에 업로드된 파일 |
| `source_unit` | 문서에서 추출한 원시 단위 (PAGE / SLIDE) |
| `section` | LLM context 단위로 분리한 의미 덩어리 |
| `chunk` | 벡터 검색 단위 (Parent-Child Retrieval의 Child) |
| `prompt_template` | 문제 생성에 사용하는 프롬프트 템플릿 |
| `generation_job` | 문제 생성 요청 1건 |
| `generation_context` | 문제 생성 시 retrieval된 Section 기록 (내부 추적/디버깅 전용) |
| `generated_quiz` | 생성된 문제 결과 |
| `notebook_qa_history` | 노트북 기반 Q&A 이력 |
| `quiz_qa_history` | 해설 기반 Q&A 이력 |

---

## 테이블 상세

### users

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| `user_id` | bigint | PK | 자동 증가 |
| `email` | varchar | UNIQUE NOT NULL | |
| `password_hash` | varchar | NOT NULL | bcrypt 암호화 저장 |
| `created_at` | timestamp | | |
| `deleted_at` | timestamp | NULL | soft delete — 탈퇴 시 소속 notebook 전체에 cascade 적용 |

---

### notebook

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| `notebook_id` | bigint | PK | 자동 증가 |
| `user_id` | bigint | FK → users | |
| `title` | varchar | NOT NULL | 기본값 "새 노트북" |
| `is_default` | boolean | | 회원가입 시 자동 생성된 기본 노트북 여부 |
| `created_at` | timestamp | | |
| `updated_at` | timestamp | | |
| `deleted_at` | timestamp | NULL | soft delete |

---

### document

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| `document_id` | bigint | PK | |
| `notebook_id` | bigint | FK → notebook | |
| `original_file_name` | varchar | NULL | 사용자가 업로드한 파일명 |
| `stored_file_name` | varchar | NULL | S3 저장 경로 |
| `file_type` | varchar | NOT NULL | `PDF` / `PPT` / `PPTX` |
| `file_size` | bigint | NULL | bytes |
| `page_count` | int | NULL | |
| `analysis_status` | varchar | NOT NULL | `UPLOADED` / `ANALYZING` / `COMPLETED` / `FAILED` |
| `summary_text` | text | NULL | 파이프라인 완료 후 저장 |
| `analysis_error_message` | text | NULL | FAILED 시 오류 내용 |
| `uploaded_at` | timestamp | NULL | |
| `analysis_started_at` | timestamp | NULL | |
| `analysis_finished_at` | timestamp | NULL | |
| `deleted_at` | timestamp | NULL | soft delete |

> `analysis_status` 전환: `UPLOADED` → `ANALYZING` → `COMPLETED` / `FAILED`
> `FAILED` 상태가 되면 즉시 soft delete 처리한다.

---

### source_unit

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| `source_unit_id` | bigint | PK | |
| `document_id` | bigint | FK → document | |
| `unit_index` | int | NOT NULL | 문서 내 순서 |
| `source_type` | varchar | NOT NULL | `PAGE` / `SLIDE` |
| `heading` | varchar | NULL | |
| `content` | text | NOT NULL | |
| `created_at` | timestamp | NOT NULL | |

> soft delete 없음. 문서 분석 파이프라인의 내부 산출물이며 cascade 정책 대상 외.

---

### section

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| `section_id` | bigint | PK | |
| `document_id` | bigint | FK → document | |
| `section_order` | int | NOT NULL | 문서 내 순서 |
| `heading` | varchar | NULL | |
| `content` | text | NOT NULL | LLM에 넘기는 전체 본문 |
| `source_start_index` | int | NOT NULL | 걸쳐 있는 SourceUnit 시작 index |
| `source_end_index` | int | NOT NULL | 걸쳐 있는 SourceUnit 끝 index |
| `source_indices` | integer[] | NOT NULL | 걸쳐 있는 전체 SourceUnit index 목록 |
| `created_at` | timestamp | NOT NULL | |
| `deleted_at` | timestamp | NULL | soft delete — document 삭제 cascade |

> 임베딩은 Section이 아닌 Chunk에만 존재한다 (Parent-Child Retrieval).

---

### chunk

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| `chunk_id` | bigint | PK | |
| `section_id` | bigint | FK → section | parent Section |
| `document_id` | bigint | FK → document | |
| `chunk_order` | int | NOT NULL | Section 내 순서 |
| `content` | text | NOT NULL | |
| `source_start_index` | int | NOT NULL | |
| `source_end_index` | int | NOT NULL | |
| `source_indices` | integer[] | NOT NULL | |
| `embedding` | vector(N) | NULL | 벡터 검색 대상. qwen3-embedding:0.6b 기준 차원 수 확정 후 반영 |
| `created_at` | timestamp | NOT NULL | |
| `deleted_at` | timestamp | NULL | soft delete + embedding NULL 초기화 — section 삭제 cascade |

> 검색은 Chunk 임베딩으로 수행하고, LLM context는 parent Section 전체를 사용한다 (Parent-Child Retrieval).

---

### prompt_template

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| `prompt_template_id` | bigint | PK | |
| `prompt_version` | varchar | UNIQUE NOT NULL | |
| `system_prompt` | text | NULL | |
| `user_prompt_template` | text | NULL | |
| `output_schema` | text | NULL | 파싱 기준 JSON 스키마 |
| `is_active` | boolean | NULL | |
| `created_at` | timestamp | NULL | |

---

### generation_job

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| `job_id` | bigint | PK | |
| `user_id` | bigint | FK → users | |
| `notebook_id` | bigint | FK → notebook | |
| `prompt_template_id` | bigint | FK → prompt_template | |
| `scope_text` | text | NULL | 사용자가 입력한 출제 범위 |
| `quiz_type` | varchar | NOT NULL | `객관식` / `단답형` / `서술형` |
| `difficulty` | varchar | NOT NULL | `상` / `중` / `하` |
| `quiz_count` | int | NULL | 요청한 문제 수 |
| `result_count` | int | NULL | 실제 생성 성공 수 |
| `status` | varchar | NOT NULL | `QUEUED` / `RUNNING` / `COMPLETED` / `PARTIAL_COMPLETED` / `FAILED` |
| `model_name` | varchar | NULL | 사용된 LLM 모델명 |
| `created_at` | timestamp | NULL | |
| `started_at` | timestamp | NULL | RUNNING 전환 시점 |
| `finished_at` | timestamp | NULL | 최종 상태 전환 시점 |
| `deleted_at` | timestamp | NULL | soft delete — notebook 삭제 cascade |

---

### generation_context

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| `context_id` | bigint | PK | |
| `job_id` | bigint | FK → generation_job | |
| `section_id` | bigint | FK → section | retrieval된 parent Section |
| `rank` | int | NULL | 유사도 순위 |
| `similarity_score` | decimal | NULL | cosine similarity 등 |
| `created_at` | timestamp | NULL | |

> 내부 추적/디버깅 전용. 사용자 API에 노출하지 않으며 cascade 삭제 대상에서 제외한다.

---

### generated_quiz

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| `quiz_id` | bigint | PK | |
| `job_id` | bigint | FK → generation_job | |
| `quiz_order` | int | NULL | job 내 순서 |
| `quiz_type` | varchar | NULL | `quiz_type` 결과 레벨 보관 |
| `question_text` | text | NULL | |
| `choices` | text | NULL | 객관식 보기 (JSON 직렬화) |
| `answer` | text | NULL | |
| `explanation` | text | NULL | |
| `source_section_ids` | bigint[] | NULL | LLM이 실제 인용한 Section ID 목록. document 삭제 시 NULL 초기화 |
| `created_at` | timestamp | NULL | |
| `deleted_at` | timestamp | NULL | soft delete — notebook / generation_job 삭제 cascade |

> `source_section_ids`가 NULL이면 원본 document가 삭제된 상태로, 생성 근거 조회(#50) 및 해설 Q&A(#51)가 불가하다.

---

### notebook_qa_history

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| `qa_history_id` | bigint | PK | |
| `user_id` | bigint | FK → users | 소유권 검증 |
| `notebook_id` | bigint | FK → notebook | 조회 기준 |
| `user_question` | text | NOT NULL | |
| `ai_answer` | text | NOT NULL | |
| `answerable` | boolean | NOT NULL | 문서 내용으로 답할 수 없으면 false |
| `cited_section_ids` | bigint[] | NULL | 답변 근거 Section ID 목록. `answerable=false`이면 NULL |
| `created_at` | timestamp | NULL | |
| `deleted_at` | timestamp | NULL | soft delete — notebook 삭제 cascade |

---

### quiz_qa_history

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| `qa_history_id` | bigint | PK | |
| `user_id` | bigint | FK → users | 소유권 검증 |
| `quiz_id` | bigint | FK → generated_quiz | 조회 기준 |
| `user_question` | text | NOT NULL | |
| `ai_answer` | text | NOT NULL | |
| `answerable` | boolean | NOT NULL | 해설과 무관한 질문이면 false |
| `created_at` | timestamp | NULL | |
| `deleted_at` | timestamp | NULL | soft delete — notebook 삭제 cascade |

> source Section은 `generated_quiz.source_section_ids`에서 직접 조회한다. `generation_context`를 참조하지 않는다.

---

## 미결 사항

| # | 항목 | 내용 |
|---|---|---|
| 1 | vector 차원 수 | `vector(N)` — qwen3-embedding:0.6b 실측 후 확정 필요 |
