# Git 커밋 메시지 규칙

기본 형식:

```text
type(scope): subject

body

footer
```

## 빠른 규칙

- `type`은 필수, 소문자 사용
- `scope`는 선택
- `subject`는 50자 이내
- 제목 끝에 마침표 사용 금지
- 제목은 명령형으로 작성
- 본문은 필요할 때만 작성
- 본문에는 무엇을, 왜 바꿨는지 작성
- 본문 줄 길이는 72자 이내 권장
- 푸터는 이슈 번호 같은 참조 정보만 작성

## 타입

| type | 의미 |
| --- | --- |
| `feat` | 기능 추가 |
| `fix` | 버그 수정 |
| `build` | 빌드, 의존성 변경 |
| `chore` | 기타 관리 작업 |
| `ci` | CI 설정 변경 |
| `docs` | 문서 수정 |
| `style` | 포맷팅, 공백, 세미콜론 등 |
| `refactor` | 동작 변화 없는 리팩터링 |
| `test` | 테스트 추가, 수정 |
| `perf` | 성능 개선 |

## 예시

좋은 예시:

```text
feat(auth): 로그인 API 추가
```

```text
fix(auth): 토큰 만료 처리 수정

만료된 토큰 요청이 500으로 처리되던 문제를 수정한다.
인증 실패를 명확히 구분해 재로그인 흐름으로 연결한다.

resolves: #21
```

```text
docs(commit): 커밋 메시지 규칙 문서 추가
```

피해야 하는 예시:

- `update`
- `bug fix`
- `최종 수정`
- `feat: added login api`
- `fix: 로그인 수정함`

## 이 저장소 권장 방식

- 가능한 한 `type(scope): subject` 형식 유지
- 한 커밋에는 한 가지 목적만 담기
- 문서 변경은 `docs`
- 빌드 설정 변경은 `build`
- 사소한 정리 작업은 `chore`

참고:
- https://velog.io/@chojs28/Git-%EC%BB%A4%EB%B0%8B-%EB%A9%94%EC%8B%9C%EC%A7%80-%EA%B7%9C%EC%B9%99
