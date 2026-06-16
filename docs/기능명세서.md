# 기능 명세서

## 기본 관계

- `user` 1:N `notebook`
- 회원가입 시 사용자에게 기본 `notebook` 1개가 자동 생성된다.
- `notebook` 1:N `document`
- `generation_job`은 특정 `notebook`에 소속된다.
- `generated_question`은 `generation_job`에 소속되며, 출제 근거는 동일 `notebook` 안의 `document -> section`에서 추적된다.

## 1. 회원가입

* **입력**: `email`, `password`
* **출력**: 회원가입 성공 여부, 생성된 사용자 식별자, 기본 노트북 식별자
* **예외**: `DuplicateEmailException`, `InvalidInputException`, `UserCreateFailedException`, `DefaultNotebookCreationFailedException`
* **관련 테이블**: `user`, `notebook`

## 2. 로그인 / 로그아웃

* **입력**: `email`, `password`
* **출력**: 로그인 성공 여부, 로그인된 사용자 정보, 로그아웃 성공 여부
* **예외**: `UserNotFoundException`, `InvalidPasswordException`, `UnauthorizedException`
* **관련 테이블**: `user`

## 3. 사용자별 데이터 분리

* **입력**: 로그인된 사용자 식별자
* **출력**: 사용자별 노트북, 문서, 문제, 생성 이력 분리 조회 결과
* **예외**: `UnauthorizedException`, `ForbiddenException`
* **관련 테이블**: `user`, `notebook`, `document`, `generation_job`, `generated_question`

## 4. 기본 노트북 자동 생성

* **입력**: 생성된 사용자 식별자
* **출력**: 기본 노트북 생성 성공 여부, 기본 노트북 식별자
* **예외**: `UserNotFoundException`, `DefaultNotebookCreationFailedException`
* **관련 테이블**: `notebook`

## 5. 노트북 목록 조회

* **입력**: `user_id`
* **출력**: 사용자가 소유한 노트북 목록
* **예외**: `UnauthorizedException`
* **관련 테이블**: `notebook`

## 6. 노트북 생성

* **입력**: `user_id`, `notebook_name`
* **출력**: 생성된 노트북 식별자
* **예외**: `UnauthorizedException`, `InvalidInputException`, `NotebookCreateFailedException`
* **관련 테이블**: `notebook`

## 7. PDF/PPT 업로드

* **입력**: `user_id`, `notebook_id`, `file`
* **출력**: 업로드 성공 여부, 저장된 문서 식별자
* **예외**: `UnauthorizedException`, `NotebookNotFoundException`, `ForbiddenException`, `InvalidFileTypeException`, `FileUploadFailedException`, `InvalidInputException`
* **관련 테이블**: `notebook`, `document`

## 8. 노트북별 업로드 문서 목록 조회

* **입력**: `user_id`, `notebook_id`
* **출력**: 해당 노트북에 속한 문서 목록
* **예외**: `UnauthorizedException`, `NotebookNotFoundException`, `ForbiddenException`
* **관련 테이블**: `notebook`, `document`

## 9. 문서 삭제

* **입력**: `user_id`, `notebook_id`, `document_id`
* **출력**: 문서 삭제 성공 여부
* **예외**: `UnauthorizedException`, `NotebookNotFoundException`, `DocumentNotFoundException`, `ForbiddenException`, `DocumentDeleteFailedException`
* **관련 테이블**: `notebook`, `document`, `section`, `generation_context`, `generated_question`

## 10. 문서 텍스트 추출

* **입력**: `notebook_id`, `document_id`, 원본 문서 파일
* **출력**: 추출된 원문 텍스트
* **예외**: `NotebookNotFoundException`, `DocumentNotFoundException`, `DocumentParseException`, `TextExtractionFailedException`
* **관련 테이블**: `notebook`, `document`

## 11. Section 단위 분리

* **입력**: 추출된 원문 텍스트
* **출력**: Section 단위로 분리된 텍스트 목록
* **예외**: `SectionSplitFailedException`, `InvalidDocumentStructureException`, `EmptyContentException`
* **관련 테이블**: `section`

## 12. Section 메타데이터 생성

* **입력**: 분리된 Section 텍스트, `document_id`
* **출력**: Section 제목, 순서, page/slide 범위 등의 메타데이터
* **예외**: `SectionMetadataGenerationFailedException`, `PageRangeMappingException`
* **관련 테이블**: `section`

## 13. Section 저장

* **입력**: `document_id`, Section 본문, Section 메타데이터
* **출력**: 저장된 Section 목록, Section 식별자 목록
* **예외**: `DocumentNotFoundException`, `SectionSaveFailedException`, `DuplicateSectionException`
* **관련 테이블**: `section`

## 14. Section 검수용 산출물 확인

* **입력**: `user_id`, `notebook_id`, `document_id`
* **출력**: 사람이 읽을 수 있는 Section 분리 결과
* **예외**: `UnauthorizedException`, `NotebookNotFoundException`, `DocumentNotFoundException`, `SectionNotFoundException`, `SectionPreviewGenerationFailedException`
* **관련 테이블**: `notebook`, `document`, `section`

## 15. 문서 분석 상태 관리

* **입력**: `document_id`, `analysis_status`
* **출력**: 문서 분석 상태 갱신 결과
* **예외**: `DocumentNotFoundException`, `InvalidAnalysisStatusException`, `InvalidStatusTransitionException`
* **관련 테이블**: `document`

## 16. Section 임베딩 생성

* **입력**: `section_id`, Section 본문
* **출력**: Section 임베딩 벡터
* **예외**: `SectionNotFoundException`, `EmptyContentException`, `EmbeddingGenerationFailedException`
* **관련 테이블**: `section`

## 17. 벡터 DB 저장

* **입력**: `section_id`, 임베딩 벡터, Section 메타데이터
* **출력**: 벡터 DB 저장 결과, `chroma_id`
* **예외**: `SectionNotFoundException`, `VectorStoreSaveFailedException`, `DuplicateVectorDocumentException`
* **관련 테이블**: `section`

## 18. 사용자 요청 임베딩 생성

* **입력**: `job_id`, 사용자 문제 생성 요청 문장
* **출력**: 요청 임베딩 벡터
* **예외**: `GenerationJobNotFoundException`, `EmptyPromptException`, `EmbeddingGenerationFailedException`
* **관련 테이블**: `generation_job`

## 19. 유사 Section 검색

* **입력**: 요청 임베딩 벡터, `notebook_id`, 검색 범위 조건
* **출력**: 해당 노트북 문서들 안에서 유사한 Section 후보 목록
* **예외**: `NotebookNotFoundException`, `VectorSearchFailedException`, `SectionSearchResultNotFoundException`, `InvalidSearchScopeException`
* **관련 테이블**: `notebook`, `section`, `generation_context`

## 20. 상위 K개 Section 선정

* **입력**: Section 후보 목록, `top_k`
* **출력**: 최종 선택된 상위 K개 Section
* **예외**: `InvalidTopKException`, `InsufficientSectionCandidatesException`
* **관련 테이블**: `generation_context`

## 21. 검색 결과 기록

* **입력**: `job_id`, `section_id`, `chroma_id`, `rank`, `similarity_score`, `section_version`
* **출력**: 검색 결과 저장 성공 여부
* **예외**: `GenerationJobNotFoundException`, `SectionNotFoundException`, `GenerationContextSaveFailedException`, `DuplicateGenerationContextException`
* **관련 테이블**: `generation_context`

## 22. 문제 생성 요청 입력

* **입력**: `user_id`, `notebook_id`, `scope_text`, `question_type`, `difficulty`, `question_count`
* **출력**: 노트북 범위 문제 생성 요청 데이터
* **예외**: `UnauthorizedException`, `NotebookNotFoundException`, `ForbiddenException`, `InvalidQuestionTypeException`, `InvalidDifficultyException`, `InvalidQuestionCountException`, `InvalidInputException`
* **관련 테이블**: `notebook`, `generation_job`

## 23. 문제 생성 요청 유효성 검사

* **입력**: `notebook_id`, `scope_text`, `question_type`, `difficulty`, `question_count`
* **출력**: 노트북 범위 문제 생성 가능 여부
* **예외**: `NotebookNotFoundException`, `NoAnalyzedDocumentInNotebookException`, `QuestionCountLimitExceededException`, `InvalidScopeException`
* **관련 테이블**: `notebook`, `document`, `section`, `generation_job`

## 24. 문제 생성 Job 생성

* **입력**: `user_id`, `notebook_id`, `scope_text`, `question_type`, `difficulty`, `question_count`
* **출력**: 생성된 `generation_job`, `job_id`
* **예외**: `UnauthorizedException`, `NotebookNotFoundException`, `ForbiddenException`, `GenerationJobSaveFailedException`, `InvalidInputException`
* **관련 테이블**: `notebook`, `generation_job`

## 25. 검색 결과 기반 문제 생성

* **입력**: `job_id`, 동일 노트북 내 문서에서 추출된 상위 K개 Section 본문, Section 메타데이터, 사용자 요청 정보
* **출력**: 생성된 문제, 정답, 해설 초안
* **예외**: `GenerationJobNotFoundException`, `GenerationContextNotFoundException`, `QuestionGenerationFailedException`, `ModelInvocationFailedException`, `InvalidModelOutputException`
* **관련 테이블**: `generation_job`, `generation_context`, `generated_question`

## 26. 문제 유형별 생성

* **입력**: `job_id`, `question_type`, 생성 컨텍스트
* **출력**: 문제 유형에 맞는 생성 결과
* **예외**: `GenerationJobNotFoundException`, `InvalidQuestionTypeException`, `QuestionGenerationFailedException`
* **관련 테이블**: `generation_job`, `generated_question`

## 27. 정답 및 해설 생성

* **입력**: `job_id`, 생성된 문제, 생성 컨텍스트
* **출력**: 문제별 정답, 해설
* **예외**: `GenerationJobNotFoundException`, `AnswerGenerationFailedException`, `ExplanationGenerationFailedException`, `InvalidModelOutputException`
* **관련 테이블**: `generated_question`

## 28. 문제 생성 상태 관리

* **입력**: `job_id`, `status`
* **출력**: 상태 갱신 결과
* **예외**: `GenerationJobNotFoundException`, `InvalidGenerationStatusException`, `InvalidStatusTransitionException`
* **관련 테이블**: `generation_job`

## 29. 생성 결과 수 기록

* **입력**: `job_id`, `result_count`
* **출력**: 생성 결과 수 저장 결과
* **예외**: `GenerationJobNotFoundException`, `InvalidResultCountException`, `GenerationJobUpdateFailedException`
* **관련 테이블**: `generation_job`

## 30. 생성 결과 저장

* **입력**: `job_id`, 문제문, 보기, 정답, 해설, `source_section_ids`, `source_document_ids`, 문제 유형
* **출력**: 저장된 문제 식별자 목록
* **예외**: `GenerationJobNotFoundException`, `GeneratedQuestionSaveFailedException`, `InvalidModelOutputException`
* **관련 테이블**: `generation_job`, `generated_question`, `generation_context`

## 31. 생성 결과 조회

* **입력**: `user_id`, `job_id`
* **출력**: 특정 노트북 범위에서 생성된 문제 목록
* **예외**: `UnauthorizedException`, `GenerationJobNotFoundException`, `ForbiddenException`, `GeneratedQuestionNotFoundException`
* **관련 테이블**: `generation_job`, `generated_question`

## 32. 문제 상세 조회

* **입력**: `user_id`, `question_id`
* **출력**: 문제문, 보기, 정답, 해설, 소속 노트북 정보, 출제 근거 문서/Section 정보
* **예외**: `UnauthorizedException`, `GeneratedQuestionNotFoundException`, `ForbiddenException`
* **관련 테이블**: `notebook`, `generated_question`, `generation_context`, `section`, `document`

## 33. 생성 이력 조회

* **입력**: `user_id`
* **출력**: 과거 문제 생성 요청 목록, 소속 노트북, 상태, 생성 시각, 결과 수
* **예외**: `UnauthorizedException`
* **관련 테이블**: `generation_job`, `notebook`

## 34. 부분 성공 결과 제공

* **입력**: `job_id`, `generation_job.status`, 생성된 문제 목록
* **출력**: 성공적으로 생성된 문제 목록
* **예외**: `GenerationJobNotFoundException`, `GeneratedQuestionNotFoundException`, `InvalidGenerationStatusException`
* **관련 테이블**: `generation_job`, `generated_question`

## 35. 생성 근거 조회

* **입력**: `job_id` 또는 `question_id`
* **출력**: 참조된 Section 목록, `source_document_id`, `source_document_name`, `rank`, `similarity_score`, `section_version`
* **예외**: `GenerationJobNotFoundException`, `GeneratedQuestionNotFoundException`, `GenerationContextNotFoundException`
* **관련 테이블**: `generation_context`, `section`, `document`

## 36. 예외 처리

* **입력**: 문서 분석, 검색, 문제 생성 과정에서 발생한 예외
* **출력**: 예외 응답, 실패 처리 결과
* **예외**: `InternalServerException`, `UnhandledException`
* **관련 테이블**: `document`, `generation_job`

## 37. 에러 상태 기록

* **입력**: `job_id` 또는 `document_id`, 에러 메시지, 종료 시점
* **출력**: 에러 기록 저장 결과
* **예외**: `GenerationJobNotFoundException`, `DocumentNotFoundException`, `ErrorLogSaveFailedException`
* **관련 테이블**: `generation_job`, `document`

## 38. 문제 생성 개수 제한

* **입력**: `notebook_id`, `question_count`
* **출력**: 생성 가능 여부
* **예외**: `NotebookNotFoundException`, `QuestionCountLimitExceededException`, `InvalidQuestionCountException`
* **관련 테이블**: `notebook`, `document`, `section`, `generation_job`

## 39. 모델 정보 기록

* **입력**: `job_id`, `model_name`, `prompt_version`
* **출력**: 모델 정보 저장 결과
* **예외**: `GenerationJobNotFoundException`, `ModelInfoSaveFailedException`
* **관련 테이블**: `generation_job`

## 40. 문제 생성 완료 시간 기록

* **입력**: `job_id`, `finished_at`
* **출력**: 완료 시간 저장 결과
* **예외**: `GenerationJobNotFoundException`, `GenerationJobUpdateFailedException`
* **관련 테이블**: `generation_job`
