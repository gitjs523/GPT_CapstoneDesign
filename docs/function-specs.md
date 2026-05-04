# 기능 명세서

## 기본 관계

- `user` 1:N `notebook`
- `notebook` 1:N `document`
- `document` 1:N `section`
- `generation_job`은 특정 `notebook`에 소속된다.
- `generation_job` 1:N `generation_context`
- `generation_context` N:1 `section`
- `generated_quiz`은 `generation_job`에 소속되며, 출제 근거는 동일 `notebook` 안의 `document -> section`에서 추적된다.
- `notebook` 1:N `notebook_qa_history` (노트북 기반 Q&A 이력)
- `generated_quiz` 1:N `quiz_qa_history` (해설 기반 Q&A 이력)

## 하드웨어 제약 배경

로컬 컴퓨터 2대에서 Ollama를 통해 임베딩 모델(`qwen3-embedding:0.6b`)과 생성 모델(`qwen3:4b-q4_K_M`)을 실행하며, ngrok 터널로 외부 접근을 허용하는 구조다. 로컬 환경 특성상 동시에 여러 모델 작업을 처리하면 메모리/CPU 과부하가 발생하므로, 임베딩·생성 모델 작업은 시스템 전체에서 한 번에 하나만 허용한다. 아래 ModelBusy 정책과 `AnalyzingDocumentExistsException`은 이 제약을 반영한 설계다.

## ModelBusy 조건 정의

ModelBusy 상태는 아래 작업 중 하나가 진행 중일 때 발생한다. 동시에 여러 모델 작업은 허용되지 않는다.

| 작업 | ModelBusy 트리거 여부 |
|------|---------------------|
| 노트북 Q&A 답변 생성 (#27) | ✓ |
| 문제 생성 (#38) | ✓ |
| 문서 분석 파이프라인 내 요약 생성 (#22) | ✗ (ANALYZING 상태로 이미 제어됨) |

ModelBusy 발생 시 차단되는 요청:
- 새 문서 업로드 (#9)
- 노트북 Q&A 질문 입력 (#24)
- 문제 생성 요청 (#31)

ModelBusy와 무관하게 항상 허용:
- 해설 기반 질문 (#51): source Section이 고정되어 있어 독립적으로 진행 가능

## 공통 정책

### 인가 원칙
모든 API는 로그인된 사용자 식별자를 기반으로 동작한다. 요청자 본인 소유 리소스에만 접근을 허용하며, 타인의 리소스 접근 시 `ForbiddenException`을 반환한다.

---

## 1. 인증

### 1. 회원가입

* **입력**: `email`, `password`
* **출력**: 회원가입 성공 여부, 생성된 사용자 식별자
* **예외**: `DuplicateEmailException`, `InvalidInputException`, `UserCreateFailedException`

### 2. 로그인 / 로그아웃

* **입력**: `email`, `password`
* **출력**: 로그인 성공 여부, 로그인된 사용자 정보, 로그아웃 성공 여부
* **예외**: `UserNotFoundException`, `InvalidPasswordException`, `UnauthorizedException`

### 3. 회원 탈퇴

* **입력**: `user_id`
* **출력**: 탈퇴 성공 여부
* **비고**: `user` soft delete 후 소속 notebook 전체에 notebook 삭제 cascade 정책(#8)을 순차 적용한다. notebook 하위 산출물 처리는 #8 정책을 따른다. notebook 행 자체는 soft delete로 보존하며, 실 삭제는 별도 retention 정책(배치)으로 처리한다.
* **예외**: `UnauthorizedException`, `UserDeleteFailedException`

---

## 2. 노트북 관리

### 4. 노트북 목록 조회

* **입력**: `user_id`
* **출력**: soft delete되지 않은 사용자 소유 노트북 목록 (각 항목에 `notebook_id`, `name`, `created_at` 포함)
* **예외**: `UnauthorizedException`

### 5. 노트북 단건 조회

* **입력**: `user_id`, `notebook_id`
* **출력**: 노트북 상세 정보 (`notebook_id`, `name`, `created_at`)
* **예외**: `UnauthorizedException`, `NotebookNotFoundException`, `ForbiddenException`

### 6. 노트북 생성

* **입력**: `user_id`, `notebook_name`
* **출력**: 생성된 노트북 식별자
* **예외**: `UnauthorizedException`, `InvalidInputException`, `NotebookCreateFailedException`

### 7. 노트북 이름 수정

* **입력**: `user_id`, `notebook_id`, `new_name`
* **출력**: 수정된 노트북 정보
* **예외**: `UnauthorizedException`, `NotebookNotFoundException`, `ForbiddenException`, `InvalidInputException`, `NotebookUpdateFailedException`

### 8. 노트북 삭제

* **입력**: `user_id`, `notebook_id`
* **출력**: 노트북 삭제(soft) 성공 여부
* **비고**: soft delete 후 소속 document 전체에 문서 삭제 cascade 정책(#12)을 순차 적용한다.

  | 산출물 | cascade 정책 | 기준 |
  |--------|------------|------|
  | document | cascade soft delete | 해당 notebook 소속 전체 |
  | section | cascade soft delete | 삭제된 document 소속 전체 |
  | chunk | cascade soft delete + embedding NULL 초기화 | 삭제된 section 소속 전체 |
  | generation_job | cascade soft delete | 해당 notebook 소속 전체 |
  | generated_quiz | cascade soft delete | soft delete된 generation_job 소속 전체 |
  | quiz_qa_history | cascade soft delete | soft delete된 generated_quiz 소속 전체 |
  | notebook_qa_history | cascade soft delete | 해당 notebook 소속 전체 |
  | generation_context | cascade 제외 | 내부 추적/디버깅용 |

* **예외**: `UnauthorizedException`, `NotebookNotFoundException`, `ForbiddenException`, `NotebookDeleteFailedException`

---

## 3. 문서 관리

### 9. 문서 업로드

* **입력**: `user_id`, `notebook_id`, `file` (허용 형식: PDF, PPT, PPTX)
* **출력**: `document_id`, `analysis_status: ANALYZING`
* **비고**: 파일을 S3에 저장한 뒤 `document_id`와 `ANALYZING` 상태를 즉시 반환한다. 이후 텍스트 추출 → Section/Chunk 분리 → 임베딩 → 요약 생성 파이프라인은 비동기로 처리된다. ModelBusy 조건 발생 시 업로드를 거부한다 (상단 ModelBusy 조건 정의 참고).
* **예외**: `UnauthorizedException`, `NotebookNotFoundException`, `ForbiddenException`, `InvalidFileTypeException`, `FileUploadFailedException`, `InvalidInputException`, `ModelBusyException`

### 10. 문서 목록 조회

* **입력**: `user_id`, `notebook_id`
* **출력**: soft delete되지 않은 문서 목록 (각 항목에 `document_id`, `file_name`, `analysis_status`, `created_at` 포함)
* **비고**: soft delete된 문서 및 FAILED 후 soft delete 처리된 문서는 목록에서 제외한다.
* **예외**: `UnauthorizedException`, `NotebookNotFoundException`, `ForbiddenException`

### 11. 문서 단건 조회

* **입력**: `user_id`, `notebook_id`, `document_id`
* **출력**: 문서 상세 정보 (`document_id`, `file_name`, `analysis_status`, `summary`, `created_at`)
* **비고**: `summary`는 `analysis_status`가 `COMPLETED`일 때만 포함된다.
* **예외**: `UnauthorizedException`, `NotebookNotFoundException`, `DocumentNotFoundException`, `ForbiddenException`

### 12. 문서 삭제

* **입력**: `user_id`, `notebook_id`, `document_id`
* **출력**: 문서 삭제(soft) 성공 여부
* **비고**: 물리적 삭제가 아닌 soft delete로 처리한다. `analysis_status`가 `ANALYZING`인 문서는 삭제할 수 없다 — 파이프라인 완료(COMPLETED / FAILED) 후 삭제 가능하다. 문서 삭제 시 아래 cascade soft delete 정책을 따른다.

  | 산출물 | cascade 정책 | 기준 |
  |--------|------------|------|
  | section | cascade soft delete | 해당 document 소속 전체 |
  | chunk | cascade soft delete + embedding NULL 초기화 | 삭제된 section 소속 전체 |
  | generated_quiz | source_section_ids NULL 초기화 | 삭제된 section이 source인 quiz만 |
  | quiz_qa_history | cascade 제외 | quiz 유지이므로 그대로 보존 |
  | notebook_qa_history | cited_section_ids NULL 초기화 | 삭제된 section이 cited인 이력만 |
  | generation_context | cascade 제외 | 내부 추적/디버깅용, 사용자에게 직접 노출되지 않음 |
* **예외**: `UnauthorizedException`, `NotebookNotFoundException`, `DocumentNotFoundException`, `ForbiddenException`, `AnalyzingDocumentException`, `DocumentDeleteFailedException`

### 13. 문서 분석 상태 조회 (polling)

* **입력**: `user_id`, `notebook_id`, `document_id`
* **출력**: `analysis_status`, `summary` (COMPLETED 상태일 때 포함)
* **비고**: 업로드(#9) 후 클라이언트가 주기적으로 polling하는 전용 엔드포인트다. soft delete된 문서도 조회 가능하다 — 파이프라인 실패 시 서버가 즉시 soft delete 처리하므로, 클라이언트가 `FAILED`를 수신하기 전에 `404`가 반환되는 것을 방지하기 위함이다. 클라이언트는 `COMPLETED` 수신 시 요약을 렌더링하고, `FAILED` 수신 시 업로드 오류를 안내한다.
* **예외**: `UnauthorizedException`, `DocumentNotFoundException`, `ForbiddenException`

### 14. Section 분리 결과 확인

* **입력**: `user_id`, `notebook_id`, `document_id`
* **출력**: Section 목록 (제목, 본문, 페이지 범위 포함)
* **비고**: `analysis_status`가 `COMPLETED`인 문서의 Section 분리 결과를 사용자가 확인할 수 있는 엔드포인트. 반환 필드 상세는 DB 스키마 확정 후 API 명세에서 정의한다.
* **예외**: `UnauthorizedException`, `NotebookNotFoundException`, `DocumentNotFoundException`, `SectionNotFoundException`, `ForbiddenException`

---

## 4. 문서 분석 파이프라인 (내부 처리)

### 15. 문서 텍스트 추출

* **입력**: `document_id`
* **출력**: 추출된 원문 텍스트
* **비고**: 원본 파일(PDF/PPT/PPTX)은 AWS S3에 저장된다. `document_id`로 document 레코드에서 S3 경로를 조회하여 파일을 읽는다. S3 저장 경로는 업로드(#9) 시점에 결정된다.
* **예외**: `DocumentNotFoundException`, `DocumentParseException`, `TextExtractionFailedException`

### 16. Section 분리 및 메타데이터 생성

* **입력**: 추출된 원문 텍스트, `document_id`
* **출력**: Section 목록 (본문, 제목, 순서, page/slide 범위 포함)
* **예외**: `SectionSplitFailedException`, `InvalidDocumentStructureException`, `EmptyContentException`, `SectionMetadataGenerationFailedException`, `PageRangeMappingException`

### 17. Section 저장

* **입력**: `document_id`, Section 본문, Section 메타데이터
* **출력**: 저장된 Section 목록, Section 식별자 목록
* **예외**: `DocumentNotFoundException`, `SectionSaveFailedException`, `DuplicateSectionException`

### 18. Chunk 분리 및 저장

* **입력**: `section_id`, Section 본문
* **출력**: 저장된 Chunk 목록, Chunk 식별자 목록
* **비고**: Section 본문을 고정 크기 또는 의미 단위로 분리하여 Chunk를 생성하고 DB에 저장한다. 각 Chunk는 parent Section(`section_id`)에 연결된다. Chunk는 임베딩 검색의 기본 단위다 (Parent-Child Retrieval).
* **예외**: `SectionNotFoundException`, `ChunkSplitFailedException`, `ChunkSaveFailedException`

### 19. 문서 분석 상태 관리

* **입력**: `document_id`, `analysis_status`
* **출력**: 문서 분석 상태 갱신 결과
* **비고**: 파이프라인 시작(ANALYZING), 완료(COMPLETED), 실패(FAILED) 시점에 호출된다. FAILED 상태 기록 직후 서버가 즉시 document soft delete 처리를 수행한다 (#12 cascade 정책 적용).
* **예외**: `DocumentNotFoundException`, `InvalidAnalysisStatusException`, `InvalidStatusTransitionException`

### 20. Chunk 임베딩 생성

* **입력**: `chunk_id`, Chunk 본문
* **출력**: Chunk 임베딩 벡터
* **비고**: Parent-Child Retrieval 구조에서 검색 단위는 Chunk(Child)다. 임베딩은 Section이 아닌 Chunk에 생성한다.
* **예외**: `ChunkNotFoundException`, `EmptyContentException`, `EmbeddingGenerationFailedException`

### 21. 벡터 DB 저장

* **입력**: `chunk_id`, 임베딩 벡터, Chunk 메타데이터
* **출력**: 벡터 DB 저장 결과 (pgvector — chunk 테이블의 임베딩 컬럼에 저장)
* **비고**: 검색은 Chunk 임베딩으로 수행하고, LLM에 전달할 문맥은 해당 Chunk의 parent Section 전체를 사용한다.
* **예외**: `ChunkNotFoundException`, `VectorStoreSaveFailedException`, `DuplicateVectorDocumentException`

### 22. 문서 요약 생성

* **입력**: `document_id`, 전체 Section 본문
* **출력**: 생성된 문서 요약 텍스트
* **예외**: `DocumentNotFoundException`, `SummaryGenerationFailedException`, `ModelInvocationFailedException`

### 23. 문서 요약 저장

* **입력**: `document_id`, 요약 텍스트
* **출력**: 요약 저장 성공 여부
* **예외**: `DocumentNotFoundException`, `SummarySaveFailedException`

---

## 5. 노트북 Q&A

### 24. 질문 입력

* **입력**: `user_id`, `notebook_id`, `question_text`
* **출력**: 질문 처리 요청 데이터
* **비고**: ModelBusy 조건 발생 시 요청을 거부한다 (상단 ModelBusy 조건 정의 참고).
* **예외**: `UnauthorizedException`, `NotebookNotFoundException`, `ForbiddenException`, `AnalyzingDocumentExistsException`, `EmptyQuestionException`, `ModelBusyException`

### 25. 질문 임베딩 생성

* **입력**: `question_text`
* **출력**: 질문 임베딩 벡터
* **예외**: `EmptyPromptException`, `EmbeddingGenerationFailedException`

### 26. 질문 기반 유사 Chunk 검색

* **입력**: 질문 임베딩 벡터, `notebook_id`
* **출력**: 노트북 내 전체 분석 완료 문서에서 유사한 Chunk 후보 목록 및 각 Chunk의 parent Section
* **비고**: pgvector로 Chunk 임베딩 유사도 검색을 수행하고, 매칭된 Chunk의 parent Section을 반환한다 (Parent-Child Retrieval).
* **예외**: `NotebookNotFoundException`, `VectorSearchFailedException`, `SectionSearchResultNotFoundException`

### 27. 검색 결과 기반 답변 생성

* **입력**: `question_text`, 상위 K개 Section 본문 및 메타데이터
* **출력**: 생성된 답변, `answerable` 여부, `citedSectionIds`
* **예외**: `QuestionAnswerGenerationFailedException`, `ModelInvocationFailedException`, `InvalidModelOutputException`

### 28. 답변 및 참조 근거 반환

* **입력**: 생성된 답변, `citedSectionIds`
* **출력**: 답변 텍스트, 참조된 Section 목록 (`sectionId`, `heading`, `documentName`)
* **예외**: `SectionNotFoundException`

### 29. 노트북 Q&A 이력 저장

* **입력**: `user_id`, `notebook_id`, `question_text`, `answer_text`, `answerable`, 참조 Section 목록
* **출력**: 저장된 이력 식별자
* **비고**: `answerable=false`인 경우 참조 Section 목록은 저장하지 않는다. 답변 불가 판정 시 유효한 출처가 없으므로 빈 상태로 저장한다.
* **예외**: `UnauthorizedException`, `NotebookNotFoundException`, `QaHistorySaveFailedException`

### 30. 노트북 Q&A 이력 조회

* **입력**: `user_id`, `notebook_id`
* **출력**: 해당 노트북의 Q&A 이력 목록 (질문, 답변, 생성 시각, 참조 Section 목록 — 각 항목은 `section_id`, `heading`, `documentName` 포함)
* **예외**: `UnauthorizedException`, `NotebookNotFoundException`, `ForbiddenException`

---

## 6. 문제 생성

### 31. 문제 생성 요청 입력

* **입력**: `user_id`, `notebook_id`, `scope_text`, `quiz_type`, `difficulty`, `quiz_count`
* **출력**: 노트북 범위 문제 생성 요청 데이터
* **비고**: ModelBusy 조건 발생 시 요청을 거부한다 (상단 ModelBusy 조건 정의 참고). `quiz_type` 허용값: `객관식` | `단답형` | `서술형`. `difficulty` 허용값: `상` | `중` | `하`.
* **예외**: `UnauthorizedException`, `NotebookNotFoundException`, `ForbiddenException`, `InvalidQuizTypeException`, `InvalidDifficultyException`, `InvalidQuizCountException`, `InvalidInputException`, `ModelBusyException`

### 32. 문제 생성 요청 유효성 검사

* **입력**: `notebook_id`, `scope_text`, `quiz_type`, `difficulty`, `quiz_count`
* **출력**: 노트북 범위 문제 생성 가능 여부
* **비고**: 최대 생성 가능 문제 수 = 노트북 내 분석 완료 문서의 전체 Section 수. 요청한 `quiz_count`가 이 상한을 초과하면 `QuizCountLimitExceededException`.
* **예외**: `NotebookNotFoundException`, `NoAnalyzedDocumentInNotebookException`, `AnalyzingDocumentExistsException`, `QuizCountLimitExceededException`, `InvalidScopeException`

### 33. 문제 생성 Job 생성

* **입력**: `user_id`, `notebook_id`, `scope_text`, `quiz_type`, `difficulty`, `quiz_count`
* **출력**: 생성된 `generation_job`, `job_id`
* **예외**: `UnauthorizedException`, `NotebookNotFoundException`, `ForbiddenException`, `GenerationJobSaveFailedException`, `InvalidInputException`

### 34. 사용자 요청 임베딩 생성

* **입력**: `job_id`, 사용자 문제 생성 요청 문장
* **출력**: 요청 임베딩 벡터
* **예외**: `GenerationJobNotFoundException`, `EmptyPromptException`, `EmbeddingGenerationFailedException`

### 35. 유사 Chunk 검색

* **입력**: 요청 임베딩 벡터, `notebook_id`, 검색 범위 조건
* **출력**: 해당 노트북 문서들 안에서 유사한 Chunk 후보 목록 및 각 Chunk의 parent Section
* **비고**: pgvector로 Chunk 임베딩 유사도 검색을 수행하고, 매칭된 Chunk의 parent Section을 반환한다 (Parent-Child Retrieval).
* **예외**: `NotebookNotFoundException`, `VectorSearchFailedException`, `SectionSearchResultNotFoundException`, `InvalidSearchScopeException`

### 36. 상위 K개 Section 선정

* **입력**: Section 후보 목록, `top_k`
* **출력**: 최종 선택된 상위 K개 Section
* **예외**: `InvalidTopKException`, `InsufficientSectionCandidatesException`

### 37. 검색 결과 기록

* **입력**: `job_id`, `section_id`, `rank`, `similarity_score`
* **출력**: 검색 결과 저장 성공 여부
* **예외**: `GenerationJobNotFoundException`, `SectionNotFoundException`, `GenerationContextSaveFailedException`, `DuplicateGenerationContextException`

### 38. 검색 결과 기반 문제 생성

* **입력**: `job_id`, 동일 노트북 내 문서에서 추출된 상위 K개 Section 본문, Section 메타데이터, 사용자 요청 정보
* **출력**: 생성된 문제, 정답, 해설 초안
* **비고**: 외부 API가 아닌 내부 서비스 함수다. #39(유형별 생성), #40(정답/해설 생성)을 오케스트레이션한다.
* **예외**: `GenerationJobNotFoundException`, `GenerationContextNotFoundException`, `QuizGenerationFailedException`, `ModelInvocationFailedException`, `InvalidModelOutputException`

### 39. 문제 유형별 생성

* **입력**: `job_id`, `quiz_type`, 생성 컨텍스트
* **출력**: 문제 유형에 맞는 생성 결과
* **비고**: #38의 내부 함수. `quiz_type`에 따라 다른 프롬프트 템플릿을 적용한다.
* **예외**: `GenerationJobNotFoundException`, `InvalidQuizTypeException`, `QuizGenerationFailedException`

### 40. 정답 및 해설 생성

* **입력**: `job_id`, 생성된 문제, 생성 컨텍스트
* **출력**: 문제별 정답, 해설
* **비고**: #38의 내부 함수. 생성된 문제에 대한 정답 및 해설을 별도 프롬프트로 생성한다.
* **예외**: `GenerationJobNotFoundException`, `AnswerGenerationFailedException`, `ExplanationGenerationFailedException`, `InvalidModelOutputException`

### 41. 문제 생성 상태 관리

* **입력**: `job_id`, `status`
* **출력**: 상태 갱신 결과
* **예외**: `GenerationJobNotFoundException`, `InvalidGenerationStatusException`, `InvalidStatusTransitionException`

### 42. 생성 결과 수 기록

* **입력**: `job_id`, `result_count`
* **출력**: 생성 결과 수 저장 결과
* **예외**: `GenerationJobNotFoundException`, `InvalidResultCountException`, `GenerationJobUpdateFailedException`

### 43. 생성 결과 저장

* **입력**: `job_id`, 문제문, 보기, 정답, 해설, 참조 Section 목록, 문제 유형
* **출력**: 저장된 문제 식별자 목록
* **예외**: `GenerationJobNotFoundException`, `GeneratedQuizSaveFailedException`, `InvalidModelOutputException`

### 44. 문제 생성 상태 조회 (polling)

* **입력**: `user_id`, `job_id`
* **출력**: `generation_job.status` (QUEUED / RUNNING / COMPLETED / PARTIAL_COMPLETED / FAILED)
* **비고**: 클라이언트가 문제 생성 요청(#31) 후 이 API를 주기적으로 polling하여 생성 완료 여부를 확인한다. COMPLETED 또는 PARTIAL_COMPLETED 상태일 때 결과 조회(#46)로 이동한다.
* **예외**: `UnauthorizedException`, `GenerationJobNotFoundException`, `ForbiddenException`

---

## 7. 문제 조회 및 편집

### 45. 생성 이력 조회

* **입력**: `user_id`, `notebook_id` (선택 — 특정 노트북 기준 필터링)
* **출력**: 과거 문제 생성 요청 목록, 소속 노트북, 상태, 생성 시각, 결과 수
* **예외**: `UnauthorizedException`, `NotebookNotFoundException` (`notebook_id` 제공 시), `ForbiddenException` (`notebook_id` 제공 시)

### 46. 생성 결과 조회

* **입력**: `user_id`, `job_id`
* **출력**: 특정 노트북 범위에서 생성된 문제 목록
* **비고**: 결과가 없는 경우(job이 아직 QUEUED/RUNNING 상태이거나 생성된 문제가 없는 경우) 빈 목록을 반환한다.
* **예외**: `UnauthorizedException`, `GenerationJobNotFoundException`, `ForbiddenException`

### 47. 문제 페이지 조회

* **입력**: `user_id`, `quiz_id`
* **출력**: 문제 제목, 보기 (정답 및 해설 미포함)
* **예외**: `UnauthorizedException`, `GeneratedQuizNotFoundException`, `ForbiddenException`

### 48. 해설 페이지 조회

* **입력**: `user_id`, `quiz_id`
* **출력**: 문제 제목, 보기, 정답, 해설
* **비고**: 문제 페이지(#47)와 해설 페이지(#48)는 동일한 `generated_quiz`를 바라보므로, notebook 삭제 cascade로 `generated_quiz`가 soft delete되면 두 페이지 모두 접근 불가가 된다. 해설 페이지는 문제 내용 없이 독립 접근 가능해야 하므로 `question_text`와 보기를 함께 반환한다.
* **예외**: `UnauthorizedException`, `GeneratedQuizNotFoundException`, `ForbiddenException`

### 49. 부분 성공 결과 제공

* **입력**: `job_id`, `generation_job.status`, 생성된 문제 목록
* **출력**: 성공적으로 생성된 문제 목록
* **비고**: 외부 API가 아닌 내부 처리 함수다. `generation_job.status`가 `PARTIAL_COMPLETED`인 경우, 파이프라인이 #38(문제 생성) 오케스트레이션 과정에서 호출하여 성공적으로 저장된 문제만 추려 반환한다. 사용자 인가 체크 없음.
* **예외**: `GenerationJobNotFoundException`, `GeneratedQuizNotFoundException`, `InvalidGenerationStatusException`

### 50. 생성 근거 조회

* **입력**: `user_id`, `quiz_id`
* **출력**: 해당 문제가 출제된 Section 목록 (`section_id`, `source_document_id`, `source_document_name`)
* **비고**: `generated_quiz.source_section_ids`를 기반으로 반환한다. `generation_context`(job 레벨 TopK 검색 기록)는 내부 추적/디버깅 전용이므로 이 API에서 노출하지 않는다. `source_section_ids`가 NULL인 경우(원본 document 삭제로 출처 소실) `QuizSourceUnavailableException`으로 거부한다.
* **예외**: `UnauthorizedException`, `GeneratedQuizNotFoundException`, `ForbiddenException`, `QuizSourceUnavailableException`

---

## 8. 해설 Q&A

### 51. 해설 기반 질문 답변

* **입력**: `user_id`, `quiz_id`, `question_text`
* **출력**: 생성된 답변, `answerable` 여부, 참조된 source Section 목록
* **비고**: pgvector 검색을 수행하지 않는다. `generated_quiz`에 저장된 source Section 목록을 직접 조회해 LLM context로 구성한다. ModelBusy 조건과 무관하게 항상 허용된다. `source_section_ids`가 NULL인 경우(원본 document 삭제로 source 소실) 해설 Q&A를 제공할 수 없으며, 요청을 `QuizSourceUnavailableException`으로 거부한다.
* **예외**: `UnauthorizedException`, `GeneratedQuizNotFoundException`, `ForbiddenException`, `EmptyQuestionException`, `QuizSourceUnavailableException`, `QuestionAnswerGenerationFailedException`, `ModelInvocationFailedException`

### 52. 해설 Q&A 이력 저장

* **입력**: `user_id`, `quiz_id`, `question_text`, `answer_text`, `answerable`
* **출력**: 저장된 이력 식별자
* **예외**: `UnauthorizedException`, `GeneratedQuizNotFoundException`, `QaHistorySaveFailedException`

### 53. 해설 Q&A 이력 조회

* **입력**: `user_id`, `quiz_id`
* **출력**: 해당 문제의 해설 Q&A 이력 목록 (질문, 답변, `answerable`, 생성 시각)
* **예외**: `UnauthorizedException`, `GeneratedQuizNotFoundException`, `ForbiddenException`

---

## 9. 시스템 공통 (내부 처리)

### 54. 예외 처리

* **입력**: 문서 분석, 검색, 문제 생성 과정에서 발생한 예외
* **출력**: 예외 응답, 실패 처리 결과
* **예외**: `InternalServerException`, `UnhandledException`

### 55. 에러 상태 기록

* **입력**: `job_id` 또는 `document_id`, 에러 메시지, 종료 시점
* **출력**: 에러 기록 저장 결과
* **예외**: `GenerationJobNotFoundException`, `DocumentNotFoundException`, `ErrorLogSaveFailedException`

### 56. 모델 정보 기록

* **입력**: `job_id`, `model_name`, `prompt_version`
* **출력**: 모델 정보 저장 결과
* **예외**: `GenerationJobNotFoundException`, `ModelInfoSaveFailedException`

### 57. 문제 생성 완료 시간 기록

* **입력**: `job_id`, `finished_at`
* **출력**: 완료 시간 저장 결과
* **예외**: `GenerationJobNotFoundException`, `GenerationJobUpdateFailedException`
