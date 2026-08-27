# Interview AI Backend 프로젝트 현황

최종 갱신일: 2026-08-28

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
- Flyway `V1__create_users.sql`, `V2__create_refresh_tokens.sql` migration
- JPA schema validation 설정

### 사용자 도메인

- `User` JPA entity
- 사용자 역할: `USER`, `ADMIN`
- 인증 제공자: `LOCAL`, `GOOGLE`, `GITHUB`
- 이메일 및 provider 계정 unique constraint
- 생성·수정 시간 자동 설정
- 로컬 사용자 생성 factory method
- JWT subject의 사용자 id를 이용한 현재 사용자 조회 service

### 인증과 보안

- Stateless Spring Security 설정
- Delegating `PasswordEncoder`
- HS256 기반 JWT encoder/decoder
- JWT claim: issuer, subject, email, role, issued-at, expires-at
- Access Token 기본 만료 시간 1시간
- Refresh Token 기본 만료 시간 14일
- 32바이트 난수 기반 opaque Refresh Token 발급
- Refresh Token 원문 대신 SHA-256 해시 저장
- 사용자별 복수 Refresh Token 저장을 통한 다중 기기 로그인 지원
- 비관적 잠금 기반 Refresh Token 회전으로 동일 토큰의 동시 재사용 방지
- JWT secret 최소 32바이트 검증
- 회원가입, 로그인 및 health endpoint 공개
- Refresh Token 기반 로그아웃 endpoint 공개
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
  - 성공 시 Bearer Access Token, Refresh Token과 각 만료 초 반환
- `POST /api/auth/refresh`
  - 인증 없이 Refresh Token 재발급 요청 가능
  - 유효한 Refresh Token을 새 값으로 회전
  - 회전된 Refresh Token과 새 Access Token 반환
  - 존재하지 않거나 만료된 Refresh Token은 HTTP 401 반환
- `POST /api/auth/logout`
  - 인증 없이 Refresh Token을 전달해 현재 기기의 세션 폐기
  - Refresh Token 원문을 저장하지 않고 SHA-256 해시로 해당 행 삭제
  - 존재하지 않거나 이미 폐기된 Refresh Token도 멱등하게 HTTP 204 반환
  - 빈 Refresh Token은 HTTP 400 반환
  - stateless Access Token은 즉시 폐기하지 않으며 기존 만료 시점까지 유효
- 전역 오류 응답
  - `DUPLICATE_EMAIL`: HTTP 409
  - `INVALID_CREDENTIALS`: HTTP 401
  - `INVALID_ACCESS_TOKEN`: HTTP 401
  - `INVALID_REFRESH_TOKEN`: HTTP 401
  - `USER_NOT_FOUND`: HTTP 404
  - `VALIDATION_ERROR`: HTTP 400 및 field 오류 정보

### 사용자 API

- `GET /api/users/me`
  - Bearer Access Token 인증 필요
  - JWT subject를 사용자 id로 변환해 DB의 최신 사용자 정보 조회
  - 사용자 id, 이메일, 닉네임, 인증 제공자, 역할 반환
  - JWT subject 형식이 잘못되면 HTTP 401 반환
  - JWT 사용자와 일치하는 사용자가 없으면 HTTP 404 반환

### 데이터베이스 통합 테스트 기반

- Testcontainers 기반 MySQL 8.4 통합 테스트 환경
- Spring Boot `@ServiceConnection`을 통한 테스트 datasource 자동 연결
- Docker를 사용할 수 없는 환경에서는 통합 테스트 자동 비활성화
- 통합 테스트 클래스 종료 후 Spring Context를 폐기해 새 Testcontainer 주소를 사용하도록 구성
- 실제 MySQL에서 Flyway V1 migration 적용 여부 검증
- `UserRepository`의 로컬 사용자 저장 및 이메일 조회 검증
- 이메일 unique constraint 위반 시 `DataIntegrityViolationException` 발생 검증

## 테스트 상태

### 작성된 자동 테스트

`AuthServiceTest`에 다음 10개 시나리오가 작성되어 있다.

- 회원가입 성공
- 비밀번호 평문 미저장
- 중복 이메일 회원가입 실패
- 로그인 성공 및 Access Token·Refresh Token 반환
- 로그인 이메일 정규화
- 잘못된 비밀번호 로그인 실패
- 존재하지 않는 이메일 로그인 실패
- 유효한 Refresh Token 회전 및 새 토큰 쌍 반환
- 유효하지 않은 Refresh Token 요청 시 Access Token 미발급
- 로그아웃 요청의 Refresh Token 폐기

`JwtTokenServiceTest`에 다음 시나리오가 작성되어 있다.

- HS256 JWT 발급 및 실제 decoder 검증
- subject, email, role claim 검증
- Access Token의 1시간 만료 시간 검증

`RefreshTokenServiceTest`에 다음 8개 시나리오가 작성되어 있다.

- Refresh Token 원문 반환 및 SHA-256 해시 저장
- 유효한 Refresh Token 회전
- 존재하지 않는 Refresh Token 거부
- 만료된 Refresh Token 거부
- 빈 Refresh Token의 Repository 조회 없는 거부
- 로그아웃할 Refresh Token의 해시 삭제
- 존재하지 않는 Refresh Token의 멱등한 폐기
- 빈 Refresh Token 폐기 요청 시 Repository 미호출

`RefreshTokenServiceIntegrationTest`에 다음 3개 MySQL 통합 시나리오가 작성되어 있다.

- 저장된 Refresh Token 로그아웃 시 DB 행 삭제
- 존재하지 않는 Refresh Token의 예외 없는 폐기
- 폐기한 Refresh Token의 재발급 거부

`AuthControllerTest`에 다음 11개 시나리오가 작성되어 있다.

- 회원가입 성공 시 HTTP 201과 응답 body 검증
- 잘못된 이메일 회원가입 요청 시 HTTP 400 검증
- 8자 미만 비밀번호 회원가입 요청 시 HTTP 400 검증
- 로그인 성공 시 HTTP 200과 Access Token, Refresh Token 및 각 만료 시간 검증
- 잘못된 로그인 정보 입력 시 HTTP 401 검증
- 빈 이메일 로그인 요청 시 HTTP 400 검증
- 유효한 Refresh Token 재발급 성공
- 빈 Refresh Token 요청 시 HTTP 400 검증
- 유효하지 않은 Refresh Token 요청 시 HTTP 401 및 `INVALID_REFRESH_TOKEN` 검증
- 로그아웃 성공 시 HTTP 204와 빈 응답 검증
- 빈 Refresh Token 로그아웃 요청 시 HTTP 400 검증

공통 인증 fixture인 `AuthFixtures`와 standalone MockMvc 설정을 제공하는 `ControllerTestSupport`가 작성되어 있다.

`SecurityConfigTest`에 다음 6개 시나리오가 작성되어 있다.

- 회원가입 endpoint의 비인증 접근 허용
- 로그인 endpoint의 비인증 접근 허용
- 보호된 endpoint의 토큰 없는 요청에 HTTP 401 반환
- 실제 HS256 JWT를 사용한 보호 endpoint 인증 성공
- Refresh Token 재발급 endpoint의 비인증 접근 허용
- 로그아웃 endpoint의 비인증 접근 허용

`UserRepositoryIntegrationTest`에 다음 3개 시나리오가 작성되어 있다.

- 실제 MySQL 8.4에 Flyway V1 migration 적용
- 로컬 사용자 저장 및 이메일 조회
- 중복 이메일 저장 시 DB unique constraint 위반

공통 Testcontainers 기반인 `MySqlIntegrationTest`가 작성되어 있으며, MySQL 연결 정보는 Spring Boot `@ServiceConnection`으로 주입한다. 여러 통합 테스트 클래스 실행 시 종료된 컨테이너의 datasource가 재사용되지 않도록 각 클래스 종료 후 Spring Context를 폐기한다.

`UserServiceTest`에 다음 4개 시나리오가 작성되어 있다.

- JWT subject에 해당하는 사용자 조회 성공
- JWT subject에 해당하는 사용자가 없을 때 실패
- JWT subject가 숫자가 아닐 때 실패
- JWT subject가 `null`일 때 실패

`UserControllerTest`에 다음 4개 시나리오가 작성되어 있다.

- JWT 인증 사용자의 정보 조회 성공
- JWT가 없는 요청에 HTTP 401 반환
- JWT 사용자와 일치하는 사용자가 없을 때 HTTP 404와 `USER_NOT_FOUND` 반환
- JWT subject가 잘못된 요청에 HTTP 401과 `INVALID_ACCESS_TOKEN` 반환

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

2026-08-26 사용자 macOS 로컬 환경에서 인증 사용자 조회 service, controller 및 전체 테스트를 실행했다.

- `./gradlew test --tests 'com.interviewai.user.service.UserServiceTest'`: 성공 (`BUILD SUCCESSFUL in 4s`, `4 actionable tasks: 3 executed, 1 up-to-date`)
- `./gradlew test --tests 'com.interviewai.user.controller.UserControllerTest'`: 성공 (`BUILD SUCCESSFUL in 3s`, `4 actionable tasks: 1 executed, 3 up-to-date`)
- `./gradlew cleanTest test`: 성공 (`BUILD SUCCESSFUL in 13s`, `5 actionable tasks: 2 executed, 3 up-to-date`)

2026-08-26 사용자 macOS 로컬 환경에서 Refresh Token 구현과 관련 테스트를 실행했다.

- `./gradlew testClasses`: 성공 (`BUILD SUCCESSFUL in 1s`, `3 actionable tasks: 2 executed, 1 up-to-date`)
- `./gradlew test --tests 'com.interviewai.auth.*'`: 성공 (`BUILD SUCCESSFUL in 2s`, `4 actionable tasks: 1 executed, 3 up-to-date`)
- `./gradlew test --tests 'com.interviewai.global.config.SecurityConfigTest'`: 성공 (`BUILD SUCCESSFUL in 3s`, `4 actionable tasks: 1 executed, 3 up-to-date`)
- `./gradlew cleanTest test`: 성공 (`BUILD SUCCESSFUL in 13s`, `5 actionable tasks: 2 executed, 3 up-to-date`)
- 전체 테스트 리포트: 40개 실행, 실패 0개, 오류 0개, 건너뜀 0개

2026-08-28 Codex 환경에서 개별 Refresh Token 로그아웃 구현과 관련 테스트를 실행했다.

- `./gradlew test --tests 'com.interviewai.auth.*' --tests 'com.interviewai.global.config.SecurityConfigTest'`: 성공 (`BUILD SUCCESSFUL in 14s`, `4 actionable tasks: 3 executed, 1 up-to-date`)
- `./gradlew cleanTest test`: 성공 (`BUILD SUCCESSFUL in 4s`, `5 actionable tasks: 2 executed, 3 up-to-date`)
- 전체 테스트 리포트: 47개 중 44개 성공, 실패 0개, 오류 0개, Docker를 사용할 수 없어 MySQL 통합 테스트 3개 건너뜀

2026-08-28 사용자 macOS 로컬 환경에서 MySQL 기반 Refresh Token 로그아웃 통합 테스트를 실행했다.

- `./gradlew cleanTest test --tests 'com.interviewai.auth.service.RefreshTokenServiceIntegrationTest'`: 성공 (`BUILD SUCCESSFUL in 12s`, `5 actionable tasks: 2 executed, 3 up-to-date`)
- 테스트 리포트: 3개 실행, 실패 0개, 오류 0개, 건너뜀 0개
- Testcontainers MySQL 8.4에서 Flyway V1·V2 migration 적용과 Refresh Token 저장·삭제를 확인함

2026-08-28 사용자 macOS 로컬 환경에서 통합 테스트 클래스 간 Testcontainer 주소 재사용 문제를 수정한 뒤 전체 테스트를 실행했다.

- `./gradlew cleanTest test`: 성공 (`BUILD SUCCESSFUL in 21s`, `5 actionable tasks: 3 executed, 2 up-to-date`)
- 전체 테스트 리포트: 50개 실행, 실패 0개, 오류 0개, 건너뜀 0개
- `MySqlIntegrationTest`에 `@DirtiesContext(AFTER_CLASS)`를 적용해 각 통합 테스트 클래스가 새 MySQL container datasource를 사용하도록 검증함

## 향후 구현 순서

현재까지 합의한 구현 순서는 다음과 같다. 특별한 설계 변경이나 선행 문제 발견이 없다면 이 순서대로 진행한다.

1. 사용자 전체 세션 폐기와 만료 Refresh Token 정리
2. OAuth2 Google 로그인 흐름
3. OAuth2 GitHub 로그인 흐름
4. 운영 환경별 설정 및 배포 구성

각 단계는 구현 코드와 관련 테스트가 모두 완료된 뒤 다음 단계로 이동한다. 구현만 끝난 경우에는 완료 처리하지 않고 `구현됨, 검증 대기`로 기록한다.

## 알려진 확인 사항

- 로컬 애플리케이션 실행에는 `MYSQL_PASSWORD`가 필요하다.
- Docker Compose 실행에는 `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD` 설정이 필요하다.
- `.env`는 Git에서 제외되며 PC마다 별도로 구성해야 한다.
- IntelliJ에서 실행할 때 Docker Compose가 읽는 `.env` 값이 Spring Boot process에 자동 전달되지는 않으므로 Run Configuration 환경변수를 별도로 설정해야 한다.
- `AuthControllerTest`는 standalone MockMvc 테스트이므로 Spring Security filter chain을 거치지 않는다.
- `SecurityConfig`의 로그인 공개 matcher는 `/api/auth/login`으로 수정되었고 filter chain 테스트로 비인증 접근을 확인했다.
- 만료된 Refresh Token은 재발급 시 거부하지만 즉시 삭제하지 않으며, 정리 방식은 로그아웃 및 Token 폐기 전략에서 결정한다.
- 개별 로그아웃은 Refresh Token 하나만 폐기하며, 이미 발급된 stateless Access Token은 만료 시점까지 유효하다.
- 동일 Refresh Token의 동시 회전에 대한 비관적 잠금 동작을 실제 MySQL에서 검증하는 동시성 통합 테스트는 아직 없다.

## 다음 작업

현재 진행 중인 작업은 없다.

다음 작업은 사용자 전체 세션 폐기와 만료 Refresh Token 정리이다. 인증된 사용자의 모든 Refresh Token을 폐기하는 API 계약과 만료 토큰의 정기 삭제 방식을 먼저 결정하고 정상·경계·실패 시나리오 테스트와 함께 구현한다.

## Git 기준점

- 기준 브랜치: `main`
- 기준 커밋: `d3af8e5 feat: Refresh Token 발급 및 재발급 구현`
- 확인 당시 `main`과 `origin/main`은 같은 커밋을 가리켰다.

## 변경 이력

- 2026-08-28: 인증 없이 호출 가능한 `/api/auth/logout` endpoint와 Refresh Token 해시 기반 개별 세션 폐기를 확인함. 존재하지 않는 토큰의 멱등 처리, 빈 토큰 검증, 비인증 접근을 포함한 관련 테스트를 확인함. Testcontainers MySQL 8.4에서 로그아웃 삭제와 폐기 토큰 재발급 거부 통합 테스트 3개를 검증하고, 통합 테스트 클래스 간 종료된 container datasource 재사용 문제를 `@DirtiesContext(AFTER_CLASS)`로 해결함. 전체 테스트 50개 성공을 확인하고 다음 작업을 사용자 전체 세션 폐기와 만료 Refresh Token 정리로 변경함.
- 2026-08-26: opaque Refresh Token 발급, SHA-256 해시 저장, 14일 만료, 비관적 잠금 기반 회전과 `/api/auth/refresh` endpoint 구현을 확인함. 인증·보안·전체 테스트 성공을 확인하고 다음 작업을 로그아웃 및 Token 폐기 전략으로 변경함. Refresh Token Repository의 MySQL 전용 통합 테스트는 후속 보강 항목으로 남김.
- 2026-08-26: JWT 인증 사용자 조회 endpoint와 subject 검증, 사용자 미존재 오류 처리를 확인함. 사용자 service/controller 테스트와 전체 테스트 성공을 확인하고, 다음 작업을 Refresh Token으로 변경함.
- 2026-08-26: Testcontainers 기반 MySQL 8.4 공통 테스트 환경과 Repository/Flyway 통합 테스트 3개를 확인함. 해당 통합 테스트 및 전체 테스트 성공을 확인하고, 다음 작업을 인증된 사용자 조회 endpoint로 변경함.
- 2026-08-25: Spring Security filter chain 테스트 4개와 로그인 공개 matcher 수정을 확인하고, 해당 테스트 및 전체 테스트 성공을 확인함. 다음 작업을 Repository 및 Flyway 통합 테스트 재설계로 변경함.
- 2026-08-25: 인증 테스트 메서드명을 영문으로 통일하고 한국어 `@DisplayName`을 추가한 뒤 전체 테스트 성공을 확인함.
- 2026-08-25: 회원가입·로그인 서비스 테스트 7개, JWT 테스트 1개, Controller 테스트 6개 및 프로젝트 전체 테스트 성공을 확인함. Controller와 JWT 테스트 단계를 완료하고 Spring Security filter chain 검증을 다음 작업으로 변경함.
- 2026-08-25: 미구현 범위를 합의된 구현 순서로 변경하고 다음 작업을 Controller API 테스트로 명시함.
- 2026-08-25: 최초 현황 문서 작성. 현재 인증 API, 보안 구성, 사용자 도메인과 작성된 테스트 범위를 코드 및 Git 이력에서 정리함.
