# Interview AI Backend 프로젝트 현황

최종 갱신일: 2026-08-26

이 문서는 다른 PC 또는 새 Codex 세션에서도 개발을 이어갈 수 있도록 현재 구현, 검증 상태와 다음 작업을 기록한다. 실제 코드와 Git 이력을 기준으로 하며, 추측한 완료 상태는 기록하지 않는다.

## 프로젝트 개요

- Spring Boot 기반 Interview AI 백엔드
- Java 21
- Gradle Wrapper 9.5.1
- Spring Boot 4.1.0
- MySQL 8.4 및 Flyway
- 기본 프로필: `local`
- 기본 서버 포트: `8080`

## 현재 구현된 기능

### 실행 환경과 데이터베이스

- Docker Compose 기반 MySQL 로컬 실행 환경
- 환경변수를 통한 DB 이름, 사용자, 비밀번호, 포트 설정
- Actuator의 `health`, `info` endpoint 노출
- Flyway `V1__create_users.sql` migration
- JPA schema validation 설정

### 사용자 도메인

- `User` JPA entity
- 사용자 역할: `USER`, `ADMIN`
- 인증 제공자: `LOCAL`, `GOOGLE`, `GITHUB`
- 이메일 및 provider 계정 unique constraint
- 생성·수정 시간 자동 설정
- 로컬 사용자 생성 factory method

### 인증과 보안

- Stateless Spring Security 설정
- Delegating `PasswordEncoder`
- HS256 기반 JWT encoder/decoder
- JWT claim: issuer, subject, email, role, issued-at, expires-at
- Access Token 기본 만료 시간 1시간
- JWT secret 최소 32바이트 검증
- 회원가입, 로그인 및 health endpoint 공개
- 그 외 요청은 인증 필요
- Spring Security filter chain 테스트로 공개·보호 endpoint와 JWT 인증 동작 검증
- 로그인 공개 matcher를 `/api/auth/login`으로 수정

### 인증 API

- `POST /api/auth/signup`
  - 이메일, 비밀번호, 닉네임 validation
  - 이메일 앞뒤 공백 제거 및 소문자 정규화
  - 비밀번호 encoding
  - 중복 이메일 사전 검사
  - DB unique constraint 충돌 재검사
  - 성공 시 HTTP 201과 사용자 id, 이메일, 닉네임 반환
- `POST /api/auth/login`
  - 이메일과 비밀번호 validation
  - LOCAL 사용자 확인
  - 비밀번호 검증
  - 성공 시 Bearer Access Token과 만료 초 반환
- 전역 오류 응답
  - `DUPLICATE_EMAIL`: HTTP 409
  - `INVALID_CREDENTIALS`: HTTP 401
  - `VALIDATION_ERROR`: HTTP 400 및 field 오류 정보

### 데이터베이스 통합 테스트 기반

- Testcontainers 기반 MySQL 8.4 통합 테스트 환경
- Spring Boot `@ServiceConnection`을 통한 테스트 datasource 자동 연결
- Docker를 사용할 수 없는 환경에서는 통합 테스트 자동 비활성화
- 실제 MySQL에서 Flyway V1 migration 적용 여부 검증
- `UserRepository`의 로컬 사용자 저장 및 이메일 조회 검증
- 이메일 unique constraint 위반 시 `DataIntegrityViolationException` 발생 검증

## 테스트 상태

### 작성된 자동 테스트

`AuthServiceTest`에 다음 7개 시나리오가 작성되어 있다.

- 회원가입 성공
- 비밀번호 평문 미저장
- 중복 이메일 회원가입 실패
- 로그인 성공 및 JWT 반환
- 로그인 이메일 정규화
- 잘못된 비밀번호 로그인 실패
- 존재하지 않는 이메일 로그인 실패

`JwtTokenServiceTest`에 다음 시나리오가 작성되어 있다.

- HS256 JWT 발급 및 실제 decoder 검증
- subject, email, role claim 검증
- Bearer token type과 1시간 만료 시간 검증

`AuthControllerTest`에 다음 6개 시나리오가 작성되어 있다.

- 회원가입 성공 시 HTTP 201과 응답 body 검증
- 잘못된 이메일 회원가입 요청 시 HTTP 400 검증
- 8자 미만 비밀번호 회원가입 요청 시 HTTP 400 검증
- 로그인 성공 시 HTTP 200과 Access Token 응답 검증
- 잘못된 로그인 정보 입력 시 HTTP 401 검증
- 빈 이메일 로그인 요청 시 HTTP 400 검증

공통 인증 fixture인 `AuthFixtures`와 standalone MockMvc 설정을 제공하는 `ControllerTestSupport`가 작성되어 있다.

`SecurityConfigTest`에 다음 4개 시나리오가 작성되어 있다.

- 회원가입 endpoint의 비인증 접근 허용
- 로그인 endpoint의 비인증 접근 허용
- 보호된 endpoint의 토큰 없는 요청에 HTTP 401 반환
- 실제 HS256 JWT를 사용한 보호 endpoint 인증 성공

`UserRepositoryIntegrationTest`에 다음 3개 시나리오가 작성되어 있다.

- 실제 MySQL 8.4에 Flyway V1 migration 적용
- 로컬 사용자 저장 및 이메일 조회
- 중복 이메일 저장 시 DB unique constraint 위반

공통 Testcontainers 기반인 `MySqlIntegrationTest`가 작성되어 있으며, MySQL 연결 정보는 Spring Boot `@ServiceConnection`으로 주입한다.

### 최근 실행 검증

2026-08-25 사용자 로컬 환경에서 다음 검증이 모두 성공했다.

- `./gradlew testClasses`: 성공
- `./gradlew test --tests 'com.interviewai.auth.service.AuthServiceTest'`: 성공
- `./gradlew test --tests 'com.interviewai.auth.service.JwtTokenServiceTest'`: 성공
- `./gradlew test --tests 'com.interviewai.auth.controller.AuthControllerTest'`: 성공
- `./gradlew test --tests 'com.interviewai.auth.*'`: 성공
- `./gradlew cleanTest test`: 성공

마지막 전체 테스트 실행 결과는 `BUILD SUCCESSFUL in 2s`, `5 actionable tasks: 2 executed, 3 up-to-date`이다.

2026-08-25 Codex 환경에서 테스트 메서드명을 영문으로 변경하고 한국어 `@DisplayName`을 추가한 뒤 `./gradlew cleanTest test`를 재실행했다. 결과는 `BUILD SUCCESSFUL in 10s`, `5 actionable tasks: 3 executed, 2 up-to-date`로 성공했다.

2026-08-25 사용자 Windows 로컬 환경에서 Spring Security filter chain 테스트와 전체 테스트를 실행했다.

- `.\gradlew.bat test --tests "com.interviewai.global.config.SecurityConfigTest"`: 성공 (`BUILD SUCCESSFUL in 6s`, `4 actionable tasks: 1 executed, 3 up-to-date`)
- `.\gradlew.bat cleanTest test`: 성공 (`BUILD SUCCESSFUL in 6s`, `5 actionable tasks: 2 executed, 3 up-to-date`)

2026-08-26 사용자 macOS 로컬 환경에서 Repository/Flyway MySQL 통합 테스트와 전체 테스트를 실행했다.

- `./gradlew test --tests 'com.interviewai.user.repository.UserRepositoryIntegrationTest'`: 성공 (`BUILD SUCCESSFUL in 13s`, `4 actionable tasks: 2 executed, 2 up-to-date`)
- `./gradlew cleanTest test`: 성공 (`BUILD SUCCESSFUL in 13s`, `5 actionable tasks: 2 executed, 3 up-to-date`)

## 향후 구현 순서

현재까지 합의한 구현 순서는 다음과 같다. 특별한 설계 변경이나 선행 문제 발견이 없다면 이 순서대로 진행한다.

1. 인증된 사용자 조회 endpoint
2. Refresh Token
3. 로그아웃 및 Token 폐기 전략
4. OAuth2 Google 로그인 흐름
5. OAuth2 GitHub 로그인 흐름
6. 운영 환경별 설정 및 배포 구성

각 단계는 구현 코드와 관련 테스트가 모두 완료된 뒤 다음 단계로 이동한다. 구현만 끝난 경우에는 완료 처리하지 않고 `구현됨, 검증 대기`로 기록한다.

## 알려진 확인 사항

- 로컬 애플리케이션 실행에는 `MYSQL_PASSWORD`가 필요하다.
- Docker Compose 실행에는 `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD` 설정이 필요하다.
- `.env`는 Git에서 제외되며 PC마다 별도로 구성해야 한다.
- IntelliJ에서 실행할 때 Docker Compose가 읽는 `.env` 값이 Spring Boot process에 자동 전달되지는 않으므로 Run Configuration 환경변수를 별도로 설정해야 한다.
- `AuthControllerTest`는 standalone MockMvc 테스트이므로 Spring Security filter chain을 거치지 않는다.
- `SecurityConfig`의 로그인 공개 matcher는 `/api/auth/login`으로 수정되었고 filter chain 테스트로 비인증 접근을 확인했다.

## 다음 작업

현재 진행 중인 작업은 없다.

다음 작업은 인증된 사용자 조회 endpoint다. JWT의 subject를 사용자 id로 사용해 현재 사용자를 조회하고, 인증 정보가 없거나 사용자가 존재하지 않는 경우를 기존 `GlobalExceptionHandler`와 `ErrorResponse` 형식으로 처리한다. 정상·경계·실패 시나리오 테스트를 함께 작성한다.

## Git 기준점

- 기준 브랜치: `main`
- 기준 커밋: `45c28de chore: 운영체제별 Git 줄바꿈 규칙 정리`
- 확인 당시 `main`과 `origin/main`은 같은 커밋을 가리켰다.

## 변경 이력

- 2026-08-26: Testcontainers 기반 MySQL 8.4 공통 테스트 환경과 Repository/Flyway 통합 테스트 3개를 확인함. 해당 통합 테스트 및 전체 테스트 성공을 확인하고, 다음 작업을 인증된 사용자 조회 endpoint로 변경함.
- 2026-08-25: Spring Security filter chain 테스트 4개와 로그인 공개 matcher 수정을 확인하고, 해당 테스트 및 전체 테스트 성공을 확인함. 다음 작업을 Repository 및 Flyway 통합 테스트 재설계로 변경함.
- 2026-08-25: 인증 테스트 메서드명을 영문으로 통일하고 한국어 `@DisplayName`을 추가한 뒤 전체 테스트 성공을 확인함.
- 2026-08-25: 회원가입·로그인 서비스 테스트 7개, JWT 테스트 1개, Controller 테스트 6개 및 프로젝트 전체 테스트 성공을 확인함. Controller와 JWT 테스트 단계를 완료하고 Spring Security filter chain 검증을 다음 작업으로 변경함.
- 2026-08-25: 미구현 범위를 합의된 구현 순서로 변경하고 다음 작업을 Controller API 테스트로 명시함.
- 2026-08-25: 최초 현황 문서 작성. 현재 인증 API, 보안 구성, 사용자 도메인과 작성된 테스트 범위를 코드 및 Git 이력에서 정리함.
