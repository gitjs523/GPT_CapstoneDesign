# 기능 명세서 / 흐름 설계도 보완 이슈

## ✅ 해결 완료

### ~~ISSUE-01. document 삭제 시 `notebook_qa_history` 노출 제한 정책 미정의 (#9)~~
**해결**: Strict cascade soft delete 정책 확정. source/cited section 중 하나라도 삭제된 document 소속이면 generated_question / notebook_qa_history soft delete. 기능명세서 #9, 흐름설계도 3.4절 반영 완료.

### ~~ISSUE-02. 기능명세서 #44와 확정된 설계 결정 간 불일치~~
**해결**: 기능명세서 #44 비고를 `generated_question`에 저장된 source Section 목록 직접 조회 방식으로 수정 완료.

### ~~ISSUE-03. `question_type` / `difficulty` 허용값 기능명세서 미기재 (#32)~~
**해결**: 기능명세서 #32 비고에 허용값 명시 완료. `question_type`: 객관식 | 단답형 | 서술형 / `difficulty`: 상 | 중 | 하.

### ~~ISSUE-04. `answerable=false` 시 `cited_section_ids` 저장 정책 미정의 (#26)~~
**해결**: 기능명세서 #26 비고에 정책 명시 완료. answerable=false 시 참조 Section 목록 저장하지 않음.

### ~~ISSUE-05. ModelBusy 조건 중앙 정의 없음~~
**해결**: 기능명세서 상단에 ModelBusy 조건 정의 섹션 추가. 트리거 작업 = {노트북 Q&A 답변 생성, 문제 생성}. 문서 분석 파이프라인 요약 생성(#18)은 제외. 흐름설계도 2.2절 반영 완료.

### ~~ISSUE-06. 최대 문제 수 계산 기준 미정의 (#52)~~
**해결**: 기능명세서 #52 비고에 공식 명시. 최대 생성 가능 문제 수 = 노트북 내 분석 완료 문서의 전체 Section 수.

### ~~ISSUE-07. 생성 이력 조회에 notebook 필터 없음 (#47)~~
**해결**: 기능명세서 #47 입력에 `notebook_id` (선택) 추가 완료.

### ~~ISSUE-08. #35 / #36 / #37 역할 경계 불분명~~
**해결**: 기능명세서 #35 비고에 "외부 API가 아닌 내부 서비스 함수, #36/#37 오케스트레이션" 명시. #36/#37에 "35의 내부 함수" 명시 완료.

### ~~ISSUE-09. 편집된 문제의 해설 Q&A 동작 방식 미정의 (#57)~~
**해결**: 기능명세서 #57 비고에 "편집 후에도 해설 Q&A는 원본 source Section 기반으로 동작" 명시 완료.

### ~~ISSUE-10. 노트북 Q&A 이력 조회 시 cited section 반환 형태 미정의 (#27)~~
**해결**: 기능명세서 #27 출력에 cited section 반환 형태 명시 완료. 각 항목: `section_id`, `heading`, `documentName`.

### ~~ISSUE-11. 원본 파일 저장 위치 미정의 (#10)~~
**해결**: AWS S3 저장 확정. 배포 환경(EC2 + RDS)과 일관성 유지. 기능명세서 #10 비고에 반영 완료.

---

## 📋 미해결 이슈

현재 미해결 이슈 없음.
