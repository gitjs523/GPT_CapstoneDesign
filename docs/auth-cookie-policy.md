# Auth Cookie Policy

## 1. 문서 목적

본 문서는 SNOW 프로젝트의 인증 쿠키 정책을 정의한다.
개발 환경과 운영 환경을 분리해서 설명하되, 인증 방식 자체는 가능한 한 동일하게 유지하는 것을 목표로 한다.

핵심 방향은 다음과 같다.

- 액세스 토큰은 짧은 수명의 토큰으로 운용한다.
- 리프레시 토큰은 `HttpOnly` 쿠키로만 전달한다.
- 리프레시 토큰은 회전(rotation) 방식으로 관리한다.
- 개발 편의 때문에 인증 모델 자체를 이중화하지 않는다.

---

## 2. 기본 인증 정책

### 2.1 토큰 정책

- **Access Token**
  - 용도: 인증이 필요한 일반 API 호출
  - 형태: JWT
  - 전달 방식: 응답 본문으로 반환 후 프론트엔드 메모리에서만 보관
  - 저장 금지: `localStorage`, `sessionStorage`
  - 권장 만료 시간: `15분`

- **Refresh Token**
  - 용도: 액세스 토큰 재발급
  - 전달 방식: `HttpOnly` 쿠키
  - 권장 만료 시간: `14일`
  - 정책: 재발급 시마다 회전(rotation)
  - 서버 저장 방식: 원문이 아니라 해시값 저장 권장

### 2.2 로그인/세션 복구 정책

- 로그인 성공 시:
  - 응답 본문에 새 `access token` 반환
  - 응답 쿠키에 새 `refresh token` 설정

- 액세스 토큰 만료 시:
  - 프론트엔드는 `POST /api/auth/refresh` 호출
  - 서버는 `refresh token` 검증 후 새 `access token` 발급
  - 동시에 `refresh token`도 회전하여 새 쿠키로 교체

- 로그아웃 시:
  - 서버는 저장된 `refresh token` 또는 토큰 패밀리를 무효화
  - 응답에서 `refresh token` 쿠키를 만료 처리

- 브라우저 새로고침 시:
  - 프론트엔드 메모리에 있던 `access token`은 사라질 수 있다.
  - 이 경우 앱 시작 시 `POST /api/auth/refresh`를 1회 호출해 세션 복구를 시도한다.

### 2.3 기본 엔드포인트 방향

- `POST /api/auth/signup`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/users/me` 또는 `GET /api/auth/me`

추가 정책:

- 회원가입 성공 시 기본 노트북 1개를 함께 생성한다.
- 회원가입 직후 자동 로그인 여부는 선택 가능하지만, 인증 모델은 동일하게 유지한다.

---

## 3. 개발용 쿠키 정책

### 3.1 개발 기준 환경

개발 환경에서는 다음 구성을 기본으로 한다.

- 프론트엔드: 로컬 React dev server
- 백엔드: 로컬 Spring Boot
- DB: 로컬 Docker Compose PostgreSQL

즉, 인증 개발은 **로컬 프론트엔드 + 로컬 백엔드** 기준으로 진행한다.
개발 단계에서 프론트엔드가 EC2 백엔드를 직접 바라보는 구조는 기본 경로로 삼지 않는다.

### 3.2 개발용 리프레시 쿠키 속성

권장 값:

- 쿠키 이름: `snow_refresh`
- `HttpOnly=true`
- `Secure=false`
- `SameSite=Lax`
- `Path=/api/auth`
- `Domain` 미설정
- `Max-Age=1209600` (`14일`)

설명:

- 로컬 개발은 `localhost` 기반으로 진행하므로 `Secure=false`로도 개발 가능하다.
- `HttpOnly`는 유지한다.
- `SameSite=Lax`는 로컬 `localhost` 환경에서 충분하며, 개발용으로 `SameSite=None`까지 내릴 필요는 없다.
- `Domain`은 지정하지 않고 host-only cookie로 유지한다.
- `Path`는 `/api/auth`로 제한해 리프레시 토큰이 필요한 경로로만 범위를 좁힌다.

### 3.3 개발용 프론트엔드/백엔드 규칙

- 프론트엔드는 쿠키 기반 요청 시 `credentials: 'include'`를 사용한다.
- 가능하면 React dev proxy를 사용해 개발 중 인증 흐름을 단순화한다.
- 프록시를 쓰지 않는 경우 Spring CORS에서 정확한 origin만 허용한다.
- 개발 단계에서 `nginx`, `mkcert`, HTTPS, 도메인 구매를 인증 구현의 필수 조건으로 두지 않는다.

### 3.4 개발 환경 예외

다음 경우에는 위 개발용 쿠키 정책이 그대로 성립하지 않을 수 있다.

- 프론트엔드 로컬 + 백엔드 EC2
- 프론트엔드 로컬 + 백엔드 ngrok/외부 도메인
- 서로 다른 site로 분리된 개발 환경

이 경우에는 사실상 운영 환경에 가까운 쿠키 정책이 필요하며,
`SameSite=None; Secure`와 HTTPS 구성이 다시 필요해질 수 있다.

---

## 4. 운영용 쿠키 정책

### 4.1 운영 기준 환경

운영 환경에서는 다음 구조를 권장한다.

- 프론트엔드: `app.example.com`
- 백엔드 API: `api.example.com`
- 둘 다 HTTPS 사용

즉, 가능하면 **같은 registrable domain 아래의 서브도메인 구조**로 맞춘다.
이렇게 하면 cross-origin일 수는 있어도 same-site로 운영하기 쉬워진다.

### 4.2 운영용 리프레시 쿠키 속성

권장 값:

- 쿠키 이름: `snow_refresh`
- `HttpOnly=true`
- `Secure=true`
- `SameSite=Lax`
- `Path=/api/auth`
- `Domain` 미설정 권장
- `Max-Age=1209600` (`14일`)

설명:

- 운영에서는 HTTPS가 필수이므로 `Secure=true`를 적용한다.
- `Domain`은 가능하면 생략해서 API 호스트 전용 cookie로 유지한다.
- 프론트엔드가 `app.example.com`이고 API가 `api.example.com`인 구조라면, 요청 시 브라우저가 `api.example.com`으로 쿠키를 전송할 수 있다.
- 같은 site 구조를 유지하면 `SameSite=Lax`로도 충분하다.

### 4.3 운영 환경에서 `SameSite=None`을 쓰는 경우

다음 상황이면 `SameSite=None; Secure`가 필요할 수 있다.

- 프론트엔드와 백엔드가 서로 다른 site
- 외부 인증 리다이렉트 흐름 때문에 third-party 문맥이 필요한 경우

이 경우 추가 주의점:

- HTTPS는 필수
- CSRF 대응을 더 엄격하게 설계해야 함
- 현재 프로젝트의 기본 권장 구조는 아님

운영 기본 권장안은 `SameSite=Lax`를 유지할 수 있는 same-site 배포 구조다.

---

## 5. 리프레시 토큰 회전 정책

리프레시 토큰은 매 재발급마다 새 값으로 교체한다.

권장 규칙:

- 로그인 시 1개의 refresh token 발급
- `refresh` 호출 성공 시:
  - 기존 refresh token 폐기
  - 새 refresh token 발급
  - 새 access token 발급
- 이미 폐기된 refresh token의 재사용이 감지되면:
  - 해당 토큰 패밀리 전체 무효화
  - 강제 재로그인 처리

서버 저장 권장 항목:

- `user_id`
- refresh token hash
- 만료 시각
- 생성 시각
- 폐기 시각
- 회전 부모/자식 관계 또는 token family 식별자
- 선택 항목: user-agent, IP, device label

---

## 6. 구현 규칙

### 6.1 프론트엔드

- 액세스 토큰은 메모리에서만 관리한다.
- 리프레시 토큰은 JS에서 읽지 않는다.
- 보호 API는 `Authorization: Bearer <access-token>` 방식으로 호출한다.
- 앱 시작 시 세션 복구가 필요하면 `refresh` API를 먼저 호출한다.

### 6.2 백엔드

- 로그인 시 `Set-Cookie`로 refresh token을 내려준다.
- 로그아웃 시 동일한 cookie name/path 기준으로 삭제 쿠키를 내려준다.
- refresh token은 DB에 평문 저장하지 않는다.
- 비밀번호 변경, 계정 탈퇴, refresh reuse 감지 시 관련 refresh token들을 무효화한다.

### 6.3 피해야 할 방식

- 개발은 `localStorage JWT`, 운영은 `HttpOnly cookie`처럼 인증 모델을 이중화하는 방식
- 운영용 실도메인 정책을 개발 중 임시 우회와 섞는 방식
- `Domain=.example.com`을 기본값처럼 넓게 여는 방식
- 보호 API 인증을 refresh cookie에 직접 의존하는 방식

---

## 7. 현재 프로젝트 기준 결론

SNOW 프로젝트의 인증 기본 방향은 다음과 같이 확정한다.

- 인증 모델은 `Access Token + Refresh Token` 구조를 사용한다.
- `access token`은 짧게 유지하고 프론트엔드 메모리에만 저장한다.
- `refresh token`은 `HttpOnly` 쿠키로만 전달한다.
- `refresh token`은 rotation 방식으로 관리한다.
- 개발 환경은 `localhost` 기반으로 단순화하여 `HttpOnly` 모델을 유지한다.
- 운영 환경은 HTTPS와 same-site 도메인 구조를 기본 전제로 한다.

이 정책을 기준으로 이후 `User/Auth` 도메인 구현을 진행한다.
