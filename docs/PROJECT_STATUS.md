# Interview AI Backend 프로젝트 현황

최종 갱신일: 2026-08-25

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

`ControllerTestSupport`와 인증 fixture가 준비되어 있지만, 현재 저장소에는 controller API 테스트가 없다.

### 최근 실행 검증

- 2026-08-25: Codex 환경에서 `./gradlew test` 실행을 시도했으나 샌드박스가 사용자 Gradle cache의 lock 파일 접근을 차단하여 실행하지 못함.
- 따라서 현재 자동 테스트의 최근 성공 여부는 이 문서에서 확인되지 않은 상태다.
- 사용자가 로컬 환경에서 테스트 완료를 알리면 실행 명령과 결과를 여기에 기록한다.

## 향후 구현 순서

현재까지 합의한 구현 순서는 다음과 같다. 특별한 설계 변경이나 선행 문제 발견이 없다면 이 순서대로 진행한다.

1. Controller validation 및 HTTP 응답 테스트
2. JWT 발급·검증 단위 테스트
3. Repository 및 Flyway 통합 테스트
4. Testcontainers 기반 MySQL 통합 테스트
5. 인증된 사용자 조회 endpoint
6. Refresh Token
7. 로그아웃 및 Token 폐기 전략
8. OAuth2 Google 로그인 흐름
9. OAuth2 GitHub 로그인 흐름
10. 운영 환경별 설정 및 배포 구성

각 단계는 구현 코드와 관련 테스트가 모두 완료된 뒤 다음 단계로 이동한다. 구현만 끝난 경우에는 완료 처리하지 않고 `구현됨, 검증 대기`로 기록한다.

## 알려진 확인 사항

- 로컬 애플리케이션 실행에는 `MYSQL_PASSWORD`가 필요하다.
- Docker Compose 실행에는 `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD` 설정이 필요하다.
- `.env`는 Git에서 제외되며 PC마다 별도로 구성해야 한다.
- IntelliJ에서 실행할 때 Docker Compose가 읽는 `.env` 값이 Spring Boot process에 자동 전달되지는 않으므로 Run Configuration 환경변수를 별도로 설정해야 한다.
- `SecurityConfig`의 로그인 공개 matcher가 `"api/auth/login"`으로 작성되어 있어 선행 `/` 누락 여부를 실제 요청 또는 security 테스트로 확인해야 한다.

## 다음 작업

현재 진행 중인 작업은 없다.

다음 작업은 1단계인 Controller validation 및 HTTP 응답 테스트다. 회원가입과 로그인 API의 정상 응답, 요청값 validation 실패, 중복 이메일, 잘못된 로그인 정보에 대한 HTTP status와 응답 body를 검증한다.

## Git 기준점

- 기준 브랜치: `main`
- 기준 커밋: `11075e5 feat: 인증 API, 전역 예외 처리`
- 확인 당시 `main`과 `origin/main`은 같은 커밋을 가리켰다.

## 변경 이력

- 2026-08-25: 미구현 범위를 합의된 구현 순서로 변경하고 다음 작업을 Controller API 테스트로 명시함.
- 2026-08-25: 최초 현황 문서 작성. 현재 인증 API, 보안 구성, 사용자 도메인과 작성된 테스트 범위를 코드 및 Git 이력에서 정리함.
