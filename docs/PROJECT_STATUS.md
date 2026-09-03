# Interview AI Backend 프로젝트 현황

최종 갱신일: 2026-09-03

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
- `prod` Spring profile 기반 운영 DB 접속 정보 환경변수 주입
- 운영 환경의 SQL 출력 비활성화, graceful shutdown 및 proxy header 처리
- reverse proxy의 forwarded proto·host·port를 반영한 Google·GitHub HTTPS OAuth2 callback URL 생성 검증
- 운영 profile의 ECS JSON 표준 출력 로그와 배포 환경명 환경변수 주입
- 컨테이너 로그의 서비스명·환경명 포함 및 JWT·OAuth2·DB secret 문자열 비노출 검증
- 상세 정보를 노출하지 않는 Actuator liveness·readiness probe 설정
- Java 21 기반 multi-stage `Dockerfile`과 non-root 애플리케이션 실행 사용자 구성 및 이미지 빌드 검증
- 빌드 산출물, IDE 설정, `.env` 등을 이미지 context에서 제외하는 `.dockerignore`
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
- 인증된 사용자의 모든 Refresh Token을 일괄 폐기하는 전체 세션 폐기 지원
- 만료 Refresh Token을 1시간 주기로 최대 1,000개씩 나누어 정리
- 한 번의 정리 실행에서는 고정된 UTC 기준 시각을 사용하고 남은 만료 토큰이 없을 때까지 Batch 반복
- MySQL의 원자적 `DELETE ... ORDER BY ... LIMIT`를 사용해 다중 인스턴스의 중복 실행을 별도 분산 락 없이 안전하게 처리
- 운영 환경에서는 정리 전용 인스턴스 1개만 `REFRESH_TOKEN_CLEANUP_ENABLED=true`로 활성화하고 일반 API 인스턴스는 비활성화
- 공통·운영 기본값은 정리 비활성화, `local` profile 기본값은 정리 활성화
- 정리 인스턴스가 중복 실행되더라도 원자적 Batch 삭제로 정합성을 유지하며 실제 DB 잠금 경합이 확인되기 전까지 분산 락을 도입하지 않음
- 그 외 요청은 인증 필요
- Spring Security filter chain 테스트로 공개·보호 endpoint와 JWT 인증 동작 검증
- 로그인 공개 matcher를 `/api/auth/login`으로 수정
- Google OIDC의 `sub`를 provider 계정 식별자로 사용하는 사용자 조회·가입·로그인 service
- 검증된 Google 이메일만 허용하고 이메일 정규화 및 기존 인증 방식과의 자동 계정 연결 차단
- GitHub의 `id`를 provider 계정 식별자로 사용하는 사용자 조회·가입·로그인 service
- GitHub `/user/emails` API에서 검증된 기본 이메일을 우선 선택하고, 기본 이메일이 없으면 첫 번째 검증 이메일 사용
- 검증된 GitHub 이메일이 없거나 GitHub 사용자 id가 누락된 인증 거부
- GitHub 사용자 이름, login, 이메일 앞부분 순서의 닉네임 대체와 DB 제한인 50자 적용
- GitHub 이메일 정규화 및 기존 인증 방식과의 자동 계정 연결 차단
- Google 인증 성공 시 기존 Access Token·Refresh Token 응답을 반환하는 success handler
- GitHub 인증 성공 시 기존 Access Token·Refresh Token 응답을 반환하도록 success handler 분기
- OAuth2 인증 실패 원인을 일반화하고 민감 정보를 노출하지 않는 failure handler
- OAuth2 인증 결과 응답의 브라우저 캐시 방지를 위한 `no-store`, `no-cache` header 적용
- 환경변수 기반 Google OAuth2 client registration과 `openid`, `profile`, `email` scope 설정
- 환경변수 기반 GitHub OAuth2 client registration과 `read:user`, `user:email` scope 설정
- OAuth2 시작·callback 경로는 `IF_REQUIRED` session을 사용하고 일반 API는 stateless를 유지하도록 Security filter chain 분리

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
- `POST /api/auth/logout-all`
    - Bearer Access Token 인증 필요
    - JWT subject의 사용자 id에 속한 모든 Refresh Token을 일괄 삭제
    - 폐기할 Refresh Token이 없어도 멱등하게 HTTP 204 반환
    - 다른 사용자의 Refresh Token은 유지
    - 이미 발급된 stateless Access Token은 즉시 폐기하지 않으며 기존 만료 시점까지 유효
- 전역 오류 응답
    - `DUPLICATE_EMAIL`: HTTP 409
    - `INVALID_CREDENTIALS`: HTTP 401
    - `INVALID_ACCESS_TOKEN`: HTTP 401
    - `INVALID_REFRESH_TOKEN`: HTTP 401
    - `INVALID_CURRENT_PASSWORD`: HTTP 401
    - `PASSWORD_CHANGE_NOT_SUPPORTED`: HTTP 400
    - `SAME_PASSWORD`: HTTP 400
    - `USER_NOT_FOUND`: HTTP 404
    - `VALIDATION_ERROR`: HTTP 400 및 field 오류 정보

### 사용자 API

- `GET /api/users/me`
    - Bearer Access Token 인증 필요
    - JWT subject를 사용자 id로 변환해 DB의 최신 사용자 정보 조회
    - 사용자 id, 이메일, 닉네임, 인증 제공자, 역할 반환
    - JWT subject 형식이 잘못되면 HTTP 401 반환
    - JWT 사용자와 일치하는 사용자가 없으면 HTTP 404 반환
- `PUT /api/users/me`
    - Bearer Access Token 인증 필요
    - 인증된 사용자 본인의 닉네임만 수정
    - 닉네임 앞뒤 공백 제거 및 2자 이상 50자 이하 validation
    - 수정된 사용자 id, 이메일, 닉네임, 인증 제공자, 역할 반환
    - JWT subject 형식이 잘못되면 HTTP 401 반환
    - JWT 사용자와 일치하는 사용자가 없으면 HTTP 404 반환
- `PUT /api/users/me/password`
    - Bearer Access Token으로 인증된 LOCAL 사용자의 비밀번호 변경
    - 현재 비밀번호 검증 후 새 비밀번호를 Delegating `PasswordEncoder`로 암호화해 저장
    - 새 비밀번호 8자 이상 64자 이하 validation 및 현재 비밀번호 재사용 거부
    - OAuth2 사용자의 비밀번호 변경 요청 거부
    - 변경 성공 시 사용자의 모든 Refresh Token을 폐기하고 HTTP 204 반환
    - 이미 발급된 stateless Access Token은 기존 만료 시점까지 유효
- `DELETE /api/users/me`
    - Bearer Access Token으로 인증된 사용자의 계정을 hard delete하고 HTTP 204 반환
    - LOCAL·Google·GitHub 사용자를 동일하게 처리
    - `ON DELETE CASCADE`로 해당 사용자의 모든 Refresh Token 삭제
    - 서비스 내부 계정만 삭제하며 Google·GitHub의 OAuth 앱 연결과 권한은 해제하지 않음
    - 탈퇴 후 같은 이메일과 OAuth2 provider 계정으로 즉시 재가입할 수 있으며 새 사용자 id를 발급
    - 이미 발급된 stateless Access Token은 만료 전까지 서명상 유효하므로 사용자 기능에서 DB 사용자 존재를 확인

### 데이터베이스 통합 테스트 기반

- Testcontainers 기반 MySQL 8.4 통합 테스트 환경
- Spring Boot `@ServiceConnection`을 통한 테스트 datasource 자동 연결
- Docker를 사용할 수 없는 환경에서는 통합 테스트 자동 비활성화
- 통합 테스트 클래스 종료 후 Spring Context를 폐기해 새 Testcontainer 주소를 사용하도록 구성
- 실제 MySQL에서 Flyway V1 migration 적용 여부 검증
- `UserRepository`의 로컬 사용자 저장 및 이메일 조회 검증
- 이메일 unique constraint 위반 시 `DataIntegrityViolationException` 발생 검증

### 인증 ERD 기준

- 인증 영역의 실제 스키마 기준은 수정 불가능한 과거 설계 문서가 아니라 순서대로 적용되는 Flyway migration이다.
- `users`
    - 기본 키: `id BIGINT AUTO_INCREMENT`
    - 사용자 이메일: `email VARCHAR(255) NOT NULL`, unique
    - 비밀번호: `password_hash VARCHAR(255) NULL`
    - 닉네임: `nickname VARCHAR(50) NOT NULL`
    - 인증 제공자: `provider VARCHAR(20) NOT NULL`, `LOCAL`, `GOOGLE`, `GITHUB`만 허용
    - OAuth2 계정 식별자: `provider_id VARCHAR(255) NULL`
    - 역할: `role VARCHAR(20) NOT NULL`, `USER`, `ADMIN`만 허용
    - 생성·수정 시각: `created_at`, `updated_at DATETIME(6) NOT NULL`
    - `provider`, `provider_id` 복합 unique constraint로 동일 provider 계정의 중복 가입 방지
    - LOCAL 사용자는 `password_hash`가 필수이고 OAuth2 사용자는 `provider_id`가 필수인 check constraint 적용
- `refresh_tokens`
    - 기본 키: `id BIGINT AUTO_INCREMENT`
    - 사용자 외래 키: `user_id BIGINT NOT NULL`, `users.id` 참조 및 사용자 삭제 시 cascade 삭제
    - 토큰 해시: `token_hash CHAR(64) NOT NULL`, unique
    - 만료 시각: `expires_at DATETIME(6) NOT NULL`
    - 생성·수정 시각: `created_at`, `updated_at DATETIME(6) NOT NULL`
    - 사용자별 조회·삭제를 위한 `user_id` index와 만료 정리를 위한 `expires_at` index 적용
- `users`와 `refresh_tokens`는 일대다 관계이며, Refresh Token 원문은 저장하지 않고 SHA-256 해시만 저장한다.

## 테스트 상태

### 작성된 자동 테스트

`AuthServiceTest`에 다음 13개 시나리오가 작성되어 있다.

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
- JWT subject에 해당하는 사용자의 모든 Refresh Token 폐기
- 숫자가 아닌 JWT subject의 전체 세션 폐기 거부
- `null` JWT subject의 전체 세션 폐기 거부

`JwtTokenServiceTest`에 다음 시나리오가 작성되어 있다.

- HS256 JWT 발급 및 실제 decoder 검증
- subject, email, role claim 검증
- Access Token의 1시간 만료 시간 검증

`RefreshTokenServiceTest`에 다음 10개 시나리오가 작성되어 있다.

- Refresh Token 원문 반환 및 SHA-256 해시 저장
- 유효한 Refresh Token 회전
- 존재하지 않는 Refresh Token 거부
- 만료된 Refresh Token 거부
- 빈 Refresh Token의 Repository 조회 없는 거부
- 로그아웃할 Refresh Token의 해시 삭제
- 존재하지 않는 Refresh Token의 멱등한 폐기
- 빈 Refresh Token 폐기 요청 시 Repository 미호출
- 사용자별 Refresh Token 전체 폐기와 삭제 건수 반환
- 폐기할 Refresh Token이 없는 사용자의 멱등 처리

`RefreshTokenServiceIntegrationTest`에 다음 4개 MySQL 통합 시나리오가 작성되어 있다.

- 저장된 Refresh Token 로그아웃 시 DB 행 삭제
- 존재하지 않는 Refresh Token의 예외 없는 폐기
- 폐기한 Refresh Token의 재발급 거부
- 대상 사용자의 모든 Refresh Token만 삭제하고 다른 사용자의 토큰 유지

`RefreshTokenCleanupSchedulerTest`에 다음 4개 시나리오가 작성되어 있다.

- Batch 크기보다 적게 삭제하면 정리 종료
- Batch가 가득 차면 같은 기준 시각으로 다음 Batch 정리
- 만료 토큰이 없는 경우의 멱등 처리
- 정리 중 예외 발생 시 후속 Batch 중단 및 예외 전파

`RefreshTokenCleanupSchedulerConfigurationTest`에 다음 4개 시나리오가 작성되어 있다.

- 정리 기능 명시적 활성화 시 Scheduler Bean 생성
- 정리 기능 명시적 비활성화 시 Scheduler Bean 미생성
- `prod` profile에서 설정 생략 시 Scheduler 기본 비활성화
- `local` profile에서 설정 생략 시 Scheduler 기본 활성화

`RefreshTokenCleanupServiceIntegrationTest`에 다음 2개 MySQL 통합 시나리오가 작성되어 있다.

- 기준 시각 이전과 정확히 같은 시각에 만료된 토큰만 삭제하고 유효 토큰 유지
- 설정한 Batch 크기만큼 오래된 만료 토큰부터 나누어 삭제

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

`SecurityConfigTest`에 다음 17개 시나리오가 작성되어 있다.

- 회원가입 endpoint의 비인증 접근 허용
- 로그인 endpoint의 비인증 접근 허용
- 보호된 endpoint의 토큰 없는 요청에 HTTP 401 반환
- 실제 HS256 JWT를 사용한 보호 endpoint 인증 성공
- Refresh Token 재발급 endpoint의 비인증 접근 허용
- 로그아웃 endpoint의 비인증 접근 허용
- 전체 세션 폐기 endpoint의 비인증 접근에 HTTP 401 반환
- JWT 인증 사용자의 subject를 전체 세션 폐기 service에 전달
- Google OAuth2 인증 시작 요청의 Google redirect와 HTTP session 생성
- Google OAuth2 callback 실패의 지정된 failure handler 전달
- GitHub OAuth2 인증 시작 요청의 GitHub redirect와 HTTP session 생성
- GitHub OAuth2 callback 실패의 지정된 failure handler 전달
- 일반 API 요청에서 HTTP session을 생성하지 않는 stateless 동작
- Health root endpoint의 비인증 접근 허용
- liveness probe의 비인증 접근 허용
- readiness probe의 비인증 접근 허용
- Health 이외 Actuator endpoint의 비인증 접근 차단

`UserRepositoryIntegrationTest`에 다음 5개 시나리오가 작성되어 있다.

- 실제 MySQL 8.4에 Flyway V1 migration 적용
- 로컬 사용자 저장 및 이메일 조회
- 중복 이메일 저장 시 DB unique constraint 위반
- 사용자 삭제 시 해당 사용자의 모든 Refresh Token cascade 삭제
- 탈퇴한 OAuth2 사용자의 동일 이메일·provider 계정 재가입

공통 Testcontainers 기반인 `MySqlIntegrationTest`가 작성되어 있으며, MySQL 연결 정보는 Spring Boot `@ServiceConnection`으로 주입한다. 여러 통합 테스트
클래스 실행 시 종료된 컨테이너의 datasource가 재사용되지 않도록 각 클래스 종료 후 Spring Context를 폐기한다.

`UserServiceTest`에 다음 16개 시나리오가 작성되어 있다.

- JWT subject에 해당하는 사용자 조회 성공
- JWT subject에 해당하는 사용자가 없을 때 실패
- JWT subject가 숫자가 아닐 때 실패
- JWT subject가 `null`일 때 실패
- JWT subject에 해당하는 사용자의 닉네임 수정 성공
- 수정할 사용자가 없을 때 실패
- 닉네임 수정 요청의 JWT subject가 숫자가 아닐 때 실패
- 로컬 사용자 비밀번호 변경과 모든 Refresh Token 폐기
- 현재 비밀번호 불일치 시 변경과 세션 폐기 거부
- 현재 비밀번호와 같은 새 비밀번호 거부
- OAuth2 사용자의 비밀번호 변경 거부
- 비밀번호를 변경할 사용자가 없을 때 실패
- 비밀번호 변경 요청의 JWT subject가 숫자가 아닐 때 실패
- JWT subject에 해당하는 사용자 삭제 성공
- 탈퇴할 사용자가 없을 때 실패
- 탈퇴 요청의 JWT subject가 숫자가 아닐 때 실패

`UserControllerTest`에 다음 24개 시나리오가 작성되어 있다.

- JWT 인증 사용자의 정보 조회 성공
- JWT가 없는 요청에 HTTP 401 반환
- JWT 사용자와 일치하는 사용자가 없을 때 HTTP 404와 `USER_NOT_FOUND` 반환
- JWT subject가 잘못된 요청에 HTTP 401과 `INVALID_ACCESS_TOKEN` 반환
- JWT 인증 사용자의 닉네임 수정 성공
- 닉네임 앞뒤 공백 제거
- 2자와 50자 닉네임 경계값 허용
- 공백 닉네임 요청에 HTTP 400과 field 오류 반환
- 1자와 51자 닉네임 요청에 HTTP 400 반환
- JWT 없는 수정 요청에 HTTP 401 반환
- 수정할 사용자가 없을 때 HTTP 404와 `USER_NOT_FOUND` 반환
- 잘못된 JWT subject의 수정 요청에 HTTP 401과 `INVALID_ACCESS_TOKEN` 반환
- JWT 인증 로컬 사용자의 비밀번호 변경 성공 시 HTTP 204 반환
- 현재 비밀번호 불일치 시 HTTP 401과 `INVALID_CURRENT_PASSWORD` 반환
- 현재 비밀번호 재사용 시 HTTP 400과 `SAME_PASSWORD` 반환
- OAuth2 사용자 요청 시 HTTP 400과 `PASSWORD_CHANGE_NOT_SUPPORTED` 반환
- 새 비밀번호 8자와 64자 경계값 허용
- 빈 현재 비밀번호 요청 시 HTTP 400과 field 오류 반환
- 새 비밀번호 8자 미만과 64자 초과 시 HTTP 400 반환
- JWT 없는 비밀번호 변경 요청에 HTTP 401 반환
- JWT 인증 사용자의 탈퇴 성공 시 HTTP 204 반환
- JWT 없는 탈퇴 요청에 HTTP 401 반환
- 탈퇴할 사용자가 없을 때 HTTP 404와 `USER_NOT_FOUND` 반환
- 잘못된 JWT subject의 탈퇴 요청에 HTTP 401과 `INVALID_ACCESS_TOKEN` 반환

`GoogleOAuth2LoginServiceTest`에 다음 8개 시나리오가 작성되어 있다.

- 기존 Google 사용자의 provider와 provider id 기반 로그인
- 신규 Google 사용자 생성과 이메일 정규화
- Google 이름 누락 시 이메일 앞부분을 닉네임으로 사용
- Google 이름을 DB 제한인 50자로 제한
- 다른 인증 방식으로 가입된 이메일의 자동 연결 거부
- 검증되지 않은 Google 이메일 거부
- Google subject 누락 거부
- Google 이메일 누락 거부

`GithubOAuth2LoginServiceTest`에 다음 9개 시나리오가 작성되어 있다.

- 기존 GitHub 사용자의 provider와 provider id 기반 로그인
- 신규 GitHub 사용자 생성과 검증 이메일 정규화
- GitHub 이름 누락 시 login을 닉네임으로 사용
- GitHub 이름과 login 누락 시 이메일 앞부분을 닉네임으로 사용
- GitHub 닉네임을 DB 제한인 50자로 제한
- 다른 인증 방식으로 가입된 이메일의 자동 연결 거부
- GitHub 사용자 정보 누락 거부
- GitHub provider id 누락 거부
- 검증된 GitHub 이메일 누락 거부

`GithubOAuth2UserServiceTest`에 다음 5개 시나리오가 작성되어 있다.

- 검증된 기본 GitHub 이메일을 사용자 속성에 추가
- 검증된 기본 이메일이 없을 때 첫 번째 검증 이메일 사용
- 검증된 GitHub 이메일이 없을 때 인증 거부
- GitHub provider id 누락 시 이메일 API를 호출하지 않고 인증 거부
- GitHub 이외 registration의 기본 사용자 정보 위임

`OAuth2AuthenticationSuccessHandlerTest`, `OAuth2AuthenticationFailureHandlerTest`에 다음 7개 시나리오가 작성되어 있다.

- Google 인증 성공 시 Access Token·Refresh Token JSON 응답과 캐시 방지 header 반환
- Google 이외 registration의 인증 성공 거부
- 기존 인증 방식과 이메일 충돌 시 HTTP 409 반환 및 토큰 미노출
- 잘못된 Google 사용자 정보에 HTTP 401 반환
- OAuth2 인증 실패 시 내부 오류와 민감 정보를 제외한 일반화된 HTTP 401 응답 반환
- GitHub 인증 성공 시 검증된 이메일과 principal을 전달하고 토큰 JSON 응답 반환
- 잘못된 GitHub 사용자 정보에 HTTP 401 반환

`ProductionConfigurationTest`에 다음 6개 시나리오가 작성되어 있다.

- `prod` profile의 운영 DB·JWT·Google/GitHub OAuth2 외부 설정 주입
- 운영 SQL 출력 비활성화, proxy header 처리 및 graceful shutdown 설정
- 상세 정보 비노출과 liveness·readiness probe 활성화
- 필수 운영 DB 비밀번호 누락 시 설정 해석 실패
- ECS JSON 형식의 표준 출력 로그와 기본 `production` 환경명 및 파일 로그 미설정
- `DEPLOYMENT_ENVIRONMENT`를 통한 로그 환경명 override

### 최근 실행 검증

2026-08-25 사용자 로컬 환경에서 다음 검증이 모두 성공했다.

- `./gradlew testClasses`: 성공
- `./gradlew test --tests 'com.interviewai.auth.service.AuthServiceTest'`: 성공
- `./gradlew test --tests 'com.interviewai.auth.service.JwtTokenServiceTest'`: 성공
- `./gradlew test --tests 'com.interviewai.auth.controller.AuthControllerTest'`: 성공
- `./gradlew test --tests 'com.interviewai.auth.*'`: 성공
- `./gradlew cleanTest test`: 성공

마지막 전체 테스트 실행 결과는 `BUILD SUCCESSFUL in 2s`, `5 actionable tasks: 2 executed, 3 up-to-date`이다.

2026-08-25 Codex 환경에서 테스트 메서드명을 영문으로 변경하고 한국어 `@DisplayName`을 추가한 뒤 `./gradlew cleanTest test`를 재실행했다. 결과는
`BUILD SUCCESSFUL in 10s`, `5 actionable tasks: 3 executed, 2 up-to-date`로 성공했다.

2026-08-25 사용자 Windows 로컬 환경에서 Spring Security filter chain 테스트와 전체 테스트를 실행했다.

- `.\gradlew.bat test --tests "com.interviewai.global.config.SecurityConfigTest"`: 성공 (`BUILD SUCCESSFUL in 6s`,
  `4 actionable tasks: 1 executed, 3 up-to-date`)
- `.\gradlew.bat cleanTest test`: 성공 (`BUILD SUCCESSFUL in 6s`, `5 actionable tasks: 2 executed, 3 up-to-date`)

2026-08-26 사용자 macOS 로컬 환경에서 Repository/Flyway MySQL 통합 테스트와 전체 테스트를 실행했다.

- `./gradlew test --tests 'com.interviewai.user.repository.UserRepositoryIntegrationTest'`: 성공 (
  `BUILD SUCCESSFUL in 13s`, `4 actionable tasks: 2 executed, 2 up-to-date`)
- `./gradlew cleanTest test`: 성공 (`BUILD SUCCESSFUL in 13s`, `5 actionable tasks: 2 executed, 3 up-to-date`)

2026-08-26 사용자 macOS 로컬 환경에서 인증 사용자 조회 service, controller 및 전체 테스트를 실행했다.

- `./gradlew test --tests 'com.interviewai.user.service.UserServiceTest'`: 성공 (`BUILD SUCCESSFUL in 4s`,
  `4 actionable tasks: 3 executed, 1 up-to-date`)
- `./gradlew test --tests 'com.interviewai.user.controller.UserControllerTest'`: 성공 (`BUILD SUCCESSFUL in 3s`,
  `4 actionable tasks: 1 executed, 3 up-to-date`)
- `./gradlew cleanTest test`: 성공 (`BUILD SUCCESSFUL in 13s`, `5 actionable tasks: 2 executed, 3 up-to-date`)

2026-08-26 사용자 macOS 로컬 환경에서 Refresh Token 구현과 관련 테스트를 실행했다.

- `./gradlew testClasses`: 성공 (`BUILD SUCCESSFUL in 1s`, `3 actionable tasks: 2 executed, 1 up-to-date`)
- `./gradlew test --tests 'com.interviewai.auth.*'`: 성공 (`BUILD SUCCESSFUL in 2s`,
  `4 actionable tasks: 1 executed, 3 up-to-date`)
- `./gradlew test --tests 'com.interviewai.global.config.SecurityConfigTest'`: 성공 (`BUILD SUCCESSFUL in 3s`,
  `4 actionable tasks: 1 executed, 3 up-to-date`)
- `./gradlew cleanTest test`: 성공 (`BUILD SUCCESSFUL in 13s`, `5 actionable tasks: 2 executed, 3 up-to-date`)
- 전체 테스트 리포트: 40개 실행, 실패 0개, 오류 0개, 건너뜀 0개

2026-08-28 Codex 환경에서 개별 Refresh Token 로그아웃 구현과 관련 테스트를 실행했다.

- `./gradlew test --tests 'com.interviewai.auth.*' --tests 'com.interviewai.global.config.SecurityConfigTest'`: 성공 (
  `BUILD SUCCESSFUL in 14s`, `4 actionable tasks: 3 executed, 1 up-to-date`)
- `./gradlew cleanTest test`: 성공 (`BUILD SUCCESSFUL in 4s`, `5 actionable tasks: 2 executed, 3 up-to-date`)
- 전체 테스트 리포트: 47개 중 44개 성공, 실패 0개, 오류 0개, Docker를 사용할 수 없어 MySQL 통합 테스트 3개 건너뜀

2026-08-28 사용자 macOS 로컬 환경에서 MySQL 기반 Refresh Token 로그아웃 통합 테스트를 실행했다.

- `./gradlew cleanTest test --tests 'com.interviewai.auth.service.RefreshTokenServiceIntegrationTest'`: 성공 (
  `BUILD SUCCESSFUL in 12s`, `5 actionable tasks: 2 executed, 3 up-to-date`)
- 테스트 리포트: 3개 실행, 실패 0개, 오류 0개, 건너뜀 0개
- Testcontainers MySQL 8.4에서 Flyway V1·V2 migration 적용과 Refresh Token 저장·삭제를 확인함

2026-08-28 사용자 macOS 로컬 환경에서 통합 테스트 클래스 간 Testcontainer 주소 재사용 문제를 수정한 뒤 전체 테스트를 실행했다.

- `./gradlew cleanTest test`: 성공 (`BUILD SUCCESSFUL in 21s`, `5 actionable tasks: 3 executed, 2 up-to-date`)
- 전체 테스트 리포트: 50개 실행, 실패 0개, 오류 0개, 건너뜀 0개
- `MySqlIntegrationTest`에 `@DirtiesContext(AFTER_CLASS)`를 적용해 각 통합 테스트 클래스가 새 MySQL container datasource를 사용하도록 검증함

2026-08-28 Codex 환경에서 사용자 전체 세션 폐기 단위·보안·통합 테스트를 추가하고 전체 테스트를 실행했다.

- `./gradlew cleanTest test`: 성공 (`BUILD SUCCESSFUL in 7s`, `5 actionable tasks: 4 executed, 1 up-to-date`)
- 전체 테스트 리포트: 58개 중 51개 성공, 실패 0개, 오류 0개, Docker를 사용할 수 없어 MySQL 통합 테스트 7개 건너뜀
- 전체 세션 폐기의 사용자별 일괄 삭제, 빈 세션 멱등 처리, JWT subject 검증, 인증 필수 endpoint 동작을 단위·보안 테스트로 검증함

2026-08-28 사용자 macOS 로컬 환경에서 MySQL 기반 사용자 전체 세션 폐기 통합 테스트를 실행했다.

- `./gradlew cleanTest test --tests 'com.interviewai.auth.service.RefreshTokenServiceIntegrationTest'`: 성공 (
  `BUILD SUCCESSFUL in 12s`, `5 actionable tasks: 2 executed, 3 up-to-date`)
- 테스트 리포트: 4개 실행, 실패 0개, 오류 0개, 건너뜀 0개
- Testcontainers MySQL 8.4에서 대상 사용자의 Refresh Token 2개 일괄 삭제, 다른 사용자 토큰 유지, 폐기 토큰 재발급 거부를 확인함

2026-08-28 Codex 환경에서 만료 Refresh Token 정리 단위·통합 테스트를 실행했다.

-

`./gradlew cleanTest test --tests 'com.interviewai.auth.scheduler.RefreshTokenCleanupSchedulerTest' --tests 'com.interviewai.auth.service.RefreshTokenCleanupServiceIntegrationTest'`:
성공 (`BUILD SUCCESSFUL in 2s`, `5 actionable tasks: 3 executed, 2 up-to-date`)

- 스케줄러 단위 테스트 4개 성공, 실패 0개, 오류 0개
- Docker를 사용할 수 없어 MySQL 통합 테스트 2개 건너뜀

2026-08-28 사용자 macOS 로컬 환경에서 MySQL 기반 만료 Refresh Token 정리 통합 테스트를 실행했다.

- `./gradlew cleanTest test --tests 'com.interviewai.auth.service.RefreshTokenCleanupServiceIntegrationTest'`: 성공 (
  `BUILD SUCCESSFUL in 12s`, `5 actionable tasks: 2 executed, 3 up-to-date`)
- 테스트 2개 실행, 실패 0개, 오류 0개, 건너뜀 0개
- Testcontainers MySQL 8.4에서 만료 경계 시각 포함 삭제, 유효 토큰 보존 및 Batch 제한 반복 삭제를 검증함

2026-08-28 사용자 Windows 로컬 환경에서 OAuth2 인증 handler 컴파일과 관련 테스트를 실행했다.

- `.\gradlew.bat testClasses`: 성공 (`BUILD SUCCESSFUL in 8s`, `3 actionable tasks: 2 executed, 1 up-to-date`)
- `.\gradlew.bat test --tests "com.interviewai.auth.handler.*"`: 성공 (`BUILD SUCCESSFUL in 4s`,
  `4 actionable tasks: 1 executed, 3 up-to-date`)
- OAuth2 성공·실패 handler의 토큰 JSON 응답, 오류 상태, 캐시 방지 header 및 민감 정보 미노출 시나리오를 검증함

2026-09-01 사용자 macOS 로컬 환경에서 Google OAuth2 client 설정과 Security filter chain 분리 관련 테스트를 실행했다.

- `./gradlew test --tests 'com.interviewai.global.config.SecurityConfigTest'`: 성공 (`BUILD SUCCESSFUL in 4s`,
  `4 actionable tasks: 2 executed, 2 up-to-date`)
- `./gradlew test --tests 'com.interviewai.user.controller.UserControllerTest'`: 성공 (`BUILD SUCCESSFUL in 4s`,
  `4 actionable tasks: 2 executed, 2 up-to-date`)
- Google scope 테스트 속성을 인덱스 형식으로 변경하고 불필요한 Security filter chain의 checked exception 선언을 제거한 뒤 `./gradlew testClasses`:
  성공 (`BUILD SUCCESSFUL in 1s`, `3 actionable tasks: 2 executed, 1 up-to-date`)
- 최종 `./gradlew cleanTest test`: 성공 (`BUILD SUCCESSFUL in 29s`, `5 actionable tasks: 2 executed, 3 up-to-date`)
- 전체 테스트 리포트: 80개 실행, 실패 0개, 오류 0개, 건너뜀 0개
- Google 인증 endpoint redirect, client registration, OAuth2 `state` 저장을 위한 session 생성, callback 실패 handler 연결과 일반 API의
  stateless 동작을 검증함

2026-09-01 사용자 Windows 로컬 환경에서 GitHub OAuth2 로그인 관련 테스트와 전체 테스트를 실행했다.

- `.\gradlew.bat test --tests "com.interviewai.auth.service.GithubOAuth2UserServiceTest"`: 성공 (`BUILD SUCCESSFUL in 6s`,
  `4 actionable tasks: 2 executed, 2 up-to-date`)
-

`.\gradlew.bat test --tests "com.interviewai.auth.service.GithubOAuth2LoginServiceTest" --tests "com.interviewai.auth.service.GithubOAuth2UserServiceTest" --tests "com.interviewai.auth.handler.OAuth2AuthenticationSuccessHandlerTest" --tests "com.interviewai.global.config.SecurityConfigTest"`:
성공 (`BUILD SUCCESSFUL in 8s`, `4 actionable tasks: 1 executed, 3 up-to-date`)

- `.\gradlew.bat test --tests "com.interviewai.user.controller.UserControllerTest"`: 성공 (`BUILD SUCCESSFUL in 9s`,
  `4 actionable tasks: 2 executed, 2 up-to-date`)
- `.\gradlew.bat cleanTest test`: 성공 (`BUILD SUCCESSFUL in 52s`, `5 actionable tasks: 2 executed, 3 up-to-date`)
- `User` entity에 Lombok `@Getter`를 적용해 timestamp 필드와 반복 getter 경고를 정리한 뒤 `.\gradlew.bat testClasses`: 성공 (
  `BUILD SUCCESSFUL in 5s`, `3 actionable tasks: 2 executed, 1 up-to-date`)
- getter 정리 후 최종 `.\gradlew.bat cleanTest test`: 성공 (`BUILD SUCCESSFUL in 52s`,
  `5 actionable tasks: 2 executed, 3 up-to-date`)
- 전체 테스트 리포트: 98개 실행, 실패 0개, 오류 0개, 건너뜀 0개
- GitHub provider id 기반 로그인, 검증 이메일 선택, 닉네임 대체, 기존 인증 방식과의 이메일 충돌 차단, 토큰 응답, OAuth2 redirect·callback과 기존 보안 테스트의 회귀 없음을
  검증함

2026-09-01 사용자 Windows 로컬 환경에서 운영 profile 설정 테스트와 전체 테스트를 실행했다.

- `.\gradlew.bat test --tests "com.interviewai.global.config.ProductionConfigurationTest"`: 성공 (
  `BUILD SUCCESSFUL in 6s`, `4 actionable tasks: 3 executed, 1 up-to-date`)
- `.\gradlew.bat cleanTest test`: 성공 (`BUILD SUCCESSFUL in 41s`, `5 actionable tasks: 2 executed, 3 up-to-date`)
- 전체 테스트 리포트: 102개 실행, 실패 0개, 오류 0개, 건너뜀 0개
- 운영 DB·인증 secret 주입, SQL 출력 비활성화, proxy header, graceful shutdown, health probe 및 필수 DB 비밀번호 누락 실패를 검증함

2026-09-01 사용자 Windows 로컬 환경에서 애플리케이션 컨테이너 이미지를 빌드했다.

- `docker build -t interview-ai-backend:local .`: 성공 (`22/22 FINISHED in 75.4s`)
- Java 21 JDK builder에서 Gradle Wrapper의 dependency 해석과 `bootJar` 생성을 확인함
- Java 21 JRE runtime 이미지에서 UID 10001의 non-root `appuser` 생성과 애플리케이션 JAR 복사를 확인함
- 로컬 이미지 `interview-ai-backend:local` 생성을 확인함

2026-09-01 사용자 Windows 로컬 환경에서 health probe 보안 수정과 실제 운영 profile 컨테이너를 검증했다.

- `.\gradlew.bat test --tests "com.interviewai.global.config.SecurityConfigTest"`: 성공 (`BUILD SUCCESSFUL in 10s`,
  `4 actionable tasks: 3 executed, 1 up-to-date`)
- `.\gradlew.bat cleanTest test`: 성공 (`BUILD SUCCESSFUL in 50s`, `5 actionable tasks: 2 executed, 3 up-to-date`)
- 전체 테스트 리포트: 106개 실행, 실패 0개, 오류 0개, 건너뜀 0개
- `docker build -t interview-ai-backend:local .`: 성공 (`22/22 FINISHED in 22.1s`)
- 컨테이너에서 `prod` profile과 non-root `appuser` 실행, MySQL 8.4 연결, Flyway V1·V2 검증 및 애플리케이션 시작을 확인함
- `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`: 모두 HTTP 200과 `UP` 응답 확인
- `/actuator/info`: 비인증 요청에 HTTP 401 응답을 확인해 Health 이외 Actuator endpoint 보호를 검증함

2026-09-01 Codex 환경에서 reverse proxy header를 적용한 OAuth2 callback URL을 실제 운영 profile 컨테이너로 검증했다.

- `interview-ai-backend:local` 이미지를 `prod` profile, non-root `appuser`, MySQL Compose network로 실행함
- `X-Forwarded-Proto: https`, `X-Forwarded-Host: api.example.com`, `X-Forwarded-Port: 443`을 전달함
- Google OAuth2 redirect의 callback URL이 `https://api.example.com/login/oauth2/code/google`로 생성되는 것을 확인함
- GitHub OAuth2 redirect의 callback URL이 `https://api.example.com/login/oauth2/code/github`로 생성되는 것을 확인함
- 두 OAuth2 응답에서 HTTP 302, `Secure; HttpOnly` session cookie, HSTS header를 확인함

2026-09-01 사용자 Windows 로컬 환경과 Codex 환경에서 운영 ECS structured logging을 검증했다.

- `.\gradlew.bat test --tests "com.interviewai.global.config.ProductionConfigurationTest"`: 성공 (
  `BUILD SUCCESSFUL in 4s`, `4 actionable tasks: 3 executed, 1 up-to-date`)
- `.\gradlew.bat cleanTest test`: 성공 (`BUILD SUCCESSFUL in 58s`, `5 actionable tasks: 2 executed, 3 up-to-date`)
- 전체 테스트 리포트: 108개 실행, 실패 0개, 오류 0개, 건너뜀 0개
- `docker build -t interview-ai-backend:local .`: 성공 (`22/22 FINISHED in 23.3s`)
- `DEPLOYMENT_ENVIRONMENT=verification`으로 실행한 운영 profile 컨테이너의 stdout 로그 28행이 모두 JSON으로 파싱되고 ECS version,
  `interview-ai-backend` 서비스명, `verification` 환경명을 포함하는 것을 확인함
- 검증용 JWT secret, Google·GitHub client secret 및 `.env`의 DB 비밀번호 문자열이 stdout 로그에 포함되지 않는 것을 확인함
- 컨테이너는 non-root `appuser`로 실행됐으며 readiness endpoint는 HTTP 200을 반환함

2026-09-01 사용자 Windows 로컬 환경에서 Refresh Token 정리 scheduler의 환경별 활성화 설정과 전체 테스트를 실행했다.

- `.\gradlew.bat test --tests "com.interviewai.auth.scheduler.*"`: 성공 (`BUILD SUCCESSFUL in 9s`,
  `4 actionable tasks: 3 executed, 1 up-to-date`)
- `.\gradlew.bat cleanTest test`: 성공 (`BUILD SUCCESSFUL in 1m 6s`, `5 actionable tasks: 2 executed, 3 up-to-date`)
- 전체 테스트 리포트: 112개 실행, 실패 0개, 오류 0개, 건너뜀 0개
- 명시적 활성화·비활성화에 따른 Scheduler Bean 생성 여부와 `prod` 기본 비활성화, `local` 기본 활성화를 검증함

2026-09-02 사용자 Windows 로컬 환경에서 회원정보 수정 관련 테스트와 전체 테스트를 실행했다.

-
`.\gradlew.bat cleanTest test --tests "com.interviewai.user.service.UserServiceTest" --tests "com.interviewai.user.controller.UserControllerTest"`:
성공 (`BUILD SUCCESSFUL in 10s`, `5 actionable tasks: 3 executed, 2 up-to-date`)
- `.\gradlew.bat cleanTest test`: 성공 (`BUILD SUCCESSFUL in 43s`, `5 actionable tasks: 2 executed, 3 up-to-date`)
- 전체 테스트 리포트: 123개 실행, 실패 0개, 오류 0개, 건너뜀 0개
- 닉네임 수정, 공백 정규화, 2자·50자 경계, validation 실패, 인증 누락, 잘못된 JWT subject와 사용자 미존재 처리를 검증함

2026-09-02 사용자 Windows 로컬 환경에서 로컬 사용자 비밀번호 변경 관련 테스트와 전체 테스트를 실행했다.

-
`.\gradlew.bat test --tests "com.interviewai.user.service.UserServiceTest" --tests "com.interviewai.user.controller.UserControllerTest"`:
성공 (`BUILD SUCCESSFUL in 32s`, `4 actionable tasks: 3 executed, 1 up-to-date`)
- `.\gradlew.bat test`: 성공 (`BUILD SUCCESSFUL in 44s`, `4 actionable tasks: 1 executed, 3 up-to-date`)
- 전체 테스트 리포트: 137개 실행, 실패 0개, 오류 0개, 건너뜀 0개
- 비밀번호 암호화 변경, 현재 비밀번호 검증, 기존 비밀번호 재사용 거부, OAuth2 사용자 거부, 8자·64자 경계, 모든 Refresh Token 폐기, validation 및 인증·사용자 오류 처리를 검증함

2026-09-03 사용자 Windows 로컬 환경에서 회원 탈퇴 관련 테스트와 전체 테스트를 실행했다.

-
`.\gradlew.bat test --tests "com.interviewai.user.service.UserServiceTest" --tests "com.interviewai.user.controller.UserControllerTest" --tests "com.interviewai.user.repository.UserRepositoryIntegrationTest"`:
성공 (`BUILD SUCCESSFUL in 46s`, `4 actionable tasks: 3 executed, 1 up-to-date`)
- `.\gradlew.bat test`: 성공 (`BUILD SUCCESSFUL in 41s`, `4 actionable tasks: 1 executed, 3 up-to-date`)
- 전체 테스트 리포트: 146개 실행, 실패 0개, 오류 0개, 건너뜀 0개
- LOCAL·OAuth2 사용자의 동일한 탈퇴 처리, HTTP 204, 인증·사용자 오류, Refresh Token cascade 삭제와 동일 OAuth2 계정 재가입을 검증함

## 향후 구현 순서

Notion의 프로젝트 기획서, 요구사항 정의서, 시스템 아키텍처 설계서, ERD 설계서, API 명세서, UI 설계서와 RAG 설계서를 기준으로 다음 순서로 진행한다. 실제 배포 환경 선정은 핵심 기능 구현 이후로
미룬다.

1. 명세와 현재 구현의 기준 정리
    - API 기준 확정 완료: base path는 `/api`, Refresh Token 재발급 URI는 `/auth/refresh`, 성공 응답은 DTO 직접 반환을 유지한다.
    - 오류 응답 기준 확정 완료: 별도 명세 오류 코드 없이 현재 `ErrorResponse` 구조와 구현된 오류 코드를 기준으로 사용한다.
    - 인증 ERD 정합성 완료: Flyway를 실제 스키마 기준으로 삼고 `password_hash`, `provider_id`, `refresh_tokens` 및 관련 제약조건을 반영한다.
2. 회원 관리 마무리
    - F-01-08 회원정보 수정 완료
    - F-01-09 로컬 사용자 비밀번호 변경 완료
    - 회원 탈퇴 범위와 OAuth2 사용자 처리 정책 확정 및 구현 완료
3. 자기소개서 관리
    - 작성·조회·수정·삭제, 버전 관리, 대표 자기소개서 설정
    - PDF 업로드와 텍스트 추출은 파일 저장 정책을 먼저 정한 후 추가
4. 이력서 관리
    - PDF 업로드·조회·수정·삭제, 대표 이력서 설정
    - 사용자별 소유권 검증과 파일 형식·크기 제한 적용
5. 기업 및 채용공고
    - 기업 검색·상세 조회·관심 기업
    - 채용공고 목록·상세 조회와 기업별 조회
    - 등록·수정·삭제는 관리자 권한과 함께 구현
6. RAG 기반 구축
    - Spring AI와 Qdrant 연결
    - 기업·채용공고·자기소개서·이력서 문서 모델과 metadata 설계
    - 전처리, 700 token chunk와 100 token overlap, embedding, metadata filtering, Top-K 5 검색
    - 사용자 문서가 다른 사용자 검색 결과에 포함되지 않도록 `userId` 필터와 통합 테스트 적용
7. AI 질문 생성과 면접 세션
    - 기업·채용공고·대표 자기소개서·대표 이력서를 선택해 면접 세션 생성
    - RAG context 기반 기술·인성·꼬리 질문 생성 및 질문 순서·상태 저장
    - RAG 검색 결과가 없을 때 기본 질문으로 fallback
8. 답변과 AI 평가
    - 답변 저장, STAR·논리성·직무 적합성 평가, 개선사항 저장
    - AI 호출 실패·timeout·재시도 및 중복 평가 방지 정책 적용
9. 면접 결과와 성장 분석
    - 면접 이력·상세 결과, 질문 유형별 통계, 강점·약점, STAR 추이와 학습 로드맵
10. 관리자 및 확장 기능
    - 기업·채용공고·RAG 문서·사용자 관리
    - 기업 정보 자동 수집과 AI 문서 재생성
    - STT/TTS, 실시간 면접, PDF 리포트는 MVP 이후 확장 범위
11. 실제 배포 구성
    - 핵심 사용자 흐름 완성 후 배포 환경을 선정하고 secret, health check, 로그 수집과 scheduler 재기동 정책을 구성

각 단계는 구현 코드와 관련 테스트가 모두 완료된 뒤 다음 단계로 이동한다. 구현만 끝난 경우에는 완료 처리하지 않고 `구현됨, 검증 대기`로 기록한다.

## 알려진 확인 사항

- 로컬 애플리케이션 실행에는 `MYSQL_PASSWORD`가 필요하다.
- Docker Compose 실행에는 `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD` 설정이 필요하다.
- `.env`는 Git에서 제외되며 PC마다 별도로 구성해야 한다.
- IntelliJ에서 실행할 때 Docker Compose가 읽는 `.env` 값이 Spring Boot process에 자동 전달되지는 않으므로 Run Configuration 환경변수를 별도로 설정해야 한다.
- `AuthControllerTest`는 standalone MockMvc 테스트이므로 Spring Security filter chain을 거치지 않는다.
- `SecurityConfig`의 로그인 공개 matcher는 `/api/auth/login`으로 수정되었고 filter chain 테스트로 비인증 접근을 확인했다.
- 만료된 Refresh Token은 재발급 시 거부하며, 기본 1시간 주기의 Scheduler가 최대 1,000개씩 나누어 DB에서 삭제한다.
- 만료 토큰 정리는 별도 분산 락을 사용하지 않는다. 운영에서는 정리 전용 인스턴스 1개만 `REFRESH_TOKEN_CLEANUP_ENABLED=true`로 활성화하고 일반 API 인스턴스는 비활성화한다. 중복
  실행 시에도 MySQL의 원자적 Batch 삭제로 결과가 멱등하며, 운영 중 DB 잠금 경합이 확인되면 분산 락 도입을 검토한다.
- 개별 로그아웃은 Refresh Token 하나만 폐기하며, 이미 발급된 stateless Access Token은 만료 시점까지 유효하다.
- 전체 세션 폐기는 요청 시점에 저장된 해당 사용자의 Refresh Token을 모두 삭제하지만, 이미 발급된 stateless Access Token은 만료 시점까지 유효하다.
- 로컬 사용자 비밀번호 변경 시 해당 사용자의 모든 Refresh Token을 폐기하지만, 이미 발급된 stateless Access Token은 만료 시점까지 유효하다.
- 동일 Refresh Token의 동시 회전에 대한 비관적 잠금 동작을 실제 MySQL에서 검증하는 동시성 통합 테스트는 아직 없다.
- OAuth2 성공 handler는 현재 토큰 쌍을 JSON으로 반환한다. 운영 배포 전 Refresh Token 전달 방식을 Secure·HttpOnly cookie 또는 일회용 교환 코드로 변경할지 결정해야
  한다.
- 로컬 및 운영 환경에서 Google 로그인을 사용하려면 `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` 환경변수와 Google Cloud Console의 승인된 redirect URI
  설정이 필요하다.
- 로컬 및 운영 환경에서 GitHub 로그인을 사용하려면 `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` 환경변수와 GitHub OAuth App의 callback URL 설정이
  필요하다.
- Google 사용자의 동시 최초 로그인에서 이메일 또는 provider 계정 unique constraint가 충돌하는 상황은 후속 통합 테스트로 검증해야 한다.
- GitHub 사용자의 동시 최초 로그인에서 이메일 또는 provider 계정 unique constraint가 충돌하는 상황은 후속 통합 테스트로 검증해야 한다.
- GitHub `/user/emails` API의 네트워크 오류와 비정상 응답은 현재 `RestClient` 예외로 전파되므로 운영 배포 전에 일반화된 OAuth2 인증 실패로 변환할지 검토해야 한다.
- API base path는 현재 구현인 `/api`를 유지하며 URL 기반 버전(`/api/v1`)은 도입하지 않는다. 외부 공개나 독립 배포 클라이언트 도입 전에 버전 정책을 다시 검토한다.
- Access Token 재발급 URI는 현재 구현인 `/api/auth/refresh`를 유지하고 Notion 명세의 `/auth/reissue`를 사용하지 않는다.
- 성공 응답은 공통 envelope 없이 endpoint별 DTO를 직접 반환하고, 목록·페이징 응답은 도메인별 전용 DTO로 정의한다. HTTP 204 응답은 body 없이 유지하며 오류 응답은 기존
  `GlobalExceptionHandler`와 `ErrorResponse` 형식을 유지한다.
- Notion 회원가입 명세의 `name`은 실제 구현의 `nickname`과 다르다.
- Notion ERD의 User에는 실제 스키마의 `provider_id`와 `refresh_tokens`가 빠져 있고 비밀번호 컬럼명도 실제 `password_hash`와 다르다.
- 문서에 정의된 Spring AI, Qdrant, OpenAI embedding, 자기소개서·이력서·기업·채용공고·면접·평가·성장·관리자 모듈은 아직 구현되지 않았다.
- RAG 설계의 `companyId AND jobPostingId AND userId` 조건은 기업 공용 문서와 사용자 전용 문서의 metadata가 다르므로 문서 유형별 필터 조합으로 구체화해야 한다.

## 다음 작업

운영 `prod` profile, DB·JWT·OAuth2 secret 주입, proxy header, graceful shutdown, health probe 설정과 자동 테스트를 완료했다. Java 21
multi-stage `Dockerfile`과 `.dockerignore`를 구현하고 `interview-ai-backend:local` 이미지 빌드, non-root 실행, MySQL 연결 및 실제 health
probe 응답까지 검증했다.

reverse proxy 환경의 OAuth2 HTTPS callback과 운영 ECS JSON 표준 출력 로그를 실제 운영 profile 컨테이너에서 검증했다. 로그 전 행에 서비스명과 배포 환경명이 포함되고, 검증
대상 JWT·OAuth2·DB secret 문자열은 포함되지 않는 것을 확인했다.

Refresh Token 정리 scheduler는 운영에서 전용 인스턴스 1개만 환경변수로 활성화하고 일반 API 인스턴스에서는 비활성화하기로 확정했다. 공통·운영 기본값은 비활성화하고 `local` profile은
기본 활성화하며, 설정 조건에 따른 Scheduler Bean 생성 여부를 자동 테스트로 검증했다.

Notion 프로젝트 문서를 기준으로 현재 구현을 대조한 결과, 인증 기반과 운영 실행 기반은 문서의 Spring Boot·Spring Security·OAuth2·JWT·MySQL·Docker 방향에 부합한다.
회원정보 수정, 로컬 사용자 비밀번호 변경과 회원 탈퇴를 현재 API 정책에 맞게 구현하고 검증했으며, 이후 핵심 도메인 및 Spring AI·Qdrant 기반 RAG는 아직 시작하지 않았다.

API 기준은 현재 구현을 기준으로 base path `/api`, Refresh Token 재발급 URI `/api/auth/refresh`, 성공 응답 DTO 직접 반환, 오류 응답 `ErrorResponse`
공통 형식으로 확정했다. 이 결정은 애플리케이션의 현재 동작과 일치하므로 코드와 테스트 변경은 없다.

별도의 명세 오류 코드 원문은 정의하지 않고 현재 구현을 공식 기준으로 사용한다. 오류 응답은 `code`, `message`, `errors` 필드로 구성하며 현재 `DUPLICATE_EMAIL`,
`INVALID_CREDENTIALS`, `INVALID_ACCESS_TOKEN`, `INVALID_REFRESH_TOKEN`, `INVALID_CURRENT_PASSWORD`,
`PASSWORD_CHANGE_NOT_SUPPORTED`, `SAME_PASSWORD`, `USER_NOT_FOUND`, `VALIDATION_ERROR`를 사용한다. 신규 오류 코드는 의미가 명확한 영문 대문자
`SNAKE_CASE`로 추가하고 HTTP 상태와 함께 `GlobalExceptionHandler`에서 관리한다.

명세와 현재 구현의 기준 정리 1단계를 완료했다. 인증 ERD는 Flyway migration을 실제 스키마 기준으로 삼고 nullable `password_hash`, nullable `provider_id`,
provider 계정 복합 unique constraint, 인증 방식 check constraint 및 별도 `refresh_tokens` 테이블을 기준으로 확정했다.

회원 탈퇴는 LOCAL·Google·GitHub 사용자를 동일하게 hard delete하고 Refresh Token과 향후 사용자 소유 데이터를 cascade 삭제하며 즉시 재가입을 허용하는 정책으로 확정했다.
Google·GitHub의 OAuth 앱 연결과 권한 해제는 서비스 탈퇴 범위에 포함하지 않는다.

다음 작업은 자기소개서 관리의 API 범위와 데이터 모델을 확정하는 것이다. 사용자별 소유권, 작성·조회·수정·삭제, 버전 관리와 대표 자기소개서 설정 정책을 먼저 정한 뒤 Flyway migration과 기능을
구현한다. PDF 업로드와 텍스트 추출은 파일 저장 정책을 확정한 후 별도 작업으로 진행한다.

실제 배포 환경 선정과 배포 플랫폼별 구성은 자기소개서·이력서, 기업·채용공고, RAG 질문 생성, 면접 답변 평가와 결과 조회로 이어지는 MVP 핵심 흐름이 완성된 뒤 진행한다.

## Git 기준점

- 기준 브랜치: `main`
- 기준 커밋: `62f2a81 feat: 로컬 사용자 비밀번호 변경 구현`
- 회원 탈퇴 구현·테스트 및 이 문서 변경은 아직 커밋되지 않은 작업 트리 변경사항이다.

## 변경 이력

- 2026-09-03: Bearer Access Token으로 인증된 사용자의 `DELETE /api/users/me` 회원 탈퇴를 구현함. LOCAL·Google·GitHub 사용자를 동일하게 hard
  delete하고 DB cascade로 모든 Refresh Token을 삭제하며, OAuth 제공자 측 연결 해제 없이 동일 이메일·provider 계정의 즉시 재가입을 허용하는 정책을 확정함. 정상·경계·실패
  테스트 9개를 추가하고 전체 테스트 146개 성공을 확인했으며 다음 작업을 자기소개서 관리 범위와 데이터 모델 확정으로 전환함.
- 2026-09-02: Bearer Access Token으로 인증된 LOCAL 사용자의 `PUT /api/users/me/password` 비밀번호 변경을 구현함. 현재 비밀번호 확인, 새 비밀번호 8자·64자
  validation, 기존 비밀번호 재사용과 OAuth2 사용자 요청 거부, 비밀번호 암호화 저장 및 모든 Refresh Token 폐기를 적용함. 정상·경계·실패 테스트 14개를 추가하고 전체 테스트 137개
  성공을 확인했으며 다음 작업을 회원 탈퇴 범위와 OAuth2 사용자 처리 정책 확정으로 전환함.
- 2026-09-02: Flyway migration을 인증 ERD의 실제 스키마 기준으로 확정하고 `users`의 `password_hash`, `provider_id`, provider 계정 unique 및
  인증 방식 check constraint와 `refresh_tokens`의 사용자 관계, token hash unique, cascade 삭제 및 index를 문서화함. 명세와 현재 구현의 기준 정리 1단계를
  완료하고 다음 작업을 로컬 사용자 비밀번호 변경으로 전환함.
- 2026-09-02: 별도의 명세 오류 코드 원문 없이 현재 구현을 오류 응답의 공식 기준으로 사용하기로 확정함. `ErrorResponse`의 `code`, `message`, `errors` 구조와 구현된
  오류 코드 6개를 유지하고 신규 오류 코드는 영문 대문자 `SNAKE_CASE`와 명시적인 HTTP 상태로 추가하기로 결정함. 다음 명세 정리 작업을 실제 Flyway schema 기반 ERD 정합성 반영으로
  전환함.
- 2026-09-02: API 기준을 현재 구현 중심으로 확정함. base path `/api`, Refresh Token 재발급 URI `/api/auth/refresh`, 성공 응답 DTO 직접 반환, HTTP
  204 body 없음, 오류 응답 `ErrorResponse` 공통 형식을 유지하기로 결정함. 애플리케이션 변경 없이 다음 명세 정리 작업을 오류 코드 매핑으로 전환함.
- 2026-09-02: Bearer Access Token으로 인증된 사용자가 자신의 닉네임을 수정하는 `PUT /api/users/me`를 구현함. 닉네임 앞뒤 공백 제거와 2자·50자 validation을
  적용하고 정상·경계·실패 테스트 11개를 추가함. 관련 테스트와 전체 테스트 123개 성공을 확인하고 다음 작업을 로컬 사용자 비밀번호 변경으로 전환함.
- 2026-09-02: Notion의 프로젝트 기획서, 요구사항 정의서, 시스템 아키텍처, ERD, API, UI 및 RAG 설계 문서를 분석하고 실제 저장소와 대조함. 인증·운영 기반은 전체 기술 방향과
  부합하지만 API version·재발급 URI·응답 형식·오류 코드와 ERD 일부가 실제 구현과 다르고 핵심 도메인은 미구현임을 기록함. 다음 개발 순서를 명세 정합성 결정, 회원 관리 마무리,
  자기소개서·이력서, 기업·채용공고, RAG, AI 질문·면접, 평가, 성장 분석, 관리자, 배포 순으로 재편함.
- 2026-09-01: Refresh Token 정리 scheduler는 운영에서 전용 인스턴스 1개만 활성화하고 일반 API 인스턴스는 비활성화하는 정책으로 확정함. 공통·운영 기본값을 비활성화하고 `local`
  기본값을 활성화했으며, 환경별 설정과 조건부 Scheduler Bean 생성 테스트 4개를 추가함. 관련 테스트와 전체 테스트 112개 성공을 확인하고 다음 작업을 대상 배포 환경 선정과 실제 배포 구성 작성으로
  변경함.
- 2026-09-01: 운영 profile의 표준 출력 로그를 ECS JSON으로 구성하고 `DEPLOYMENT_ENVIRONMENT` 환경명 주입을 추가함. 설정 테스트와 전체 테스트 108개, 이미지 재빌드
  성공을 확인했으며 실제 컨테이너 stdout 28행 전체의 JSON·ECS 형식, 서비스명·환경명 포함, 검증 대상 JWT·OAuth2·DB secret 문자열 비노출을 확인함. 다음 작업을 Refresh
  Token 정리 scheduler의 다중 인스턴스 실행 정책으로 변경함.
- 2026-09-01: 실제 운영 profile 컨테이너에 reverse proxy의 HTTPS proto·host·port header를 전달해 Google·GitHub OAuth2 callback URL이 외부
  HTTPS 주소로 생성되는 것을 확인함. HTTP 302와 Secure·HttpOnly session cookie 및 HSTS header를 검증하고 다음 작업을 운영 로그 수집과 민감정보 제외 정책으로 변경함.
- 2026-09-01: Health root만 공개되어 liveness·readiness probe가 HTTP 401을 반환하는 문제를 확인하고 `/actuator/health/**`를 공개 matcher에
  추가함. 보안 테스트와 전체 테스트 106개 성공, 이미지 재빌드, non-root 운영 profile 컨테이너 실행, MySQL·Flyway 연결을 확인했으며 Health 3개 경로의 HTTP 200·`UP`과
  `/actuator/info`의 HTTP 401을 실제 컨테이너에서 검증함.
- 2026-09-01: 운영 `prod` profile에 DB·인증 secret 환경변수 주입, SQL 출력 비활성화, proxy header, graceful shutdown 및 health probe 설정을
  추가함. 운영 설정 테스트 4개 및 전체 테스트 102개 성공을 확인하고, Java 21 multi-stage 컨테이너 이미지와 non-root 실행 구성을 작성해
  `interview-ai-backend:local` 이미지 빌드를 검증함. 실제 컨테이너 실행과 health check 검증은 후속 작업으로 남김.
- 2026-09-01: GitHub `id` 기반 사용자 가입·로그인, `/user/emails`의 검증 이메일 선택, 이메일 충돌 차단, JWT·Refresh Token 발급과 OAuth2 성공 handler
  분기를 확인함. GitHub client registration, redirect·callback 보안 흐름 및 정상·경계·실패 테스트를 추가했으며 Windows 로컬 환경에서 전체 테스트 98개 성공을 확인하고
  다음 작업을 운영 환경별 설정 및 배포 구성으로 변경함.
- 2026-09-01: 환경변수 기반 Google OAuth2 client registration을 추가하고 OAuth2 시작·callback 경로에만 session을 허용하도록 Security filter
  chain을 분리함. Google 인증 redirect와 `state` 저장 session, callback 실패 handler 연결, 일반 API의 stateless 동작을 검증했으며 전체 테스트 80개 성공을
  확인하고 다음 작업을 OAuth2 GitHub 로그인 흐름으로 변경함.
- 2026-08-28: Google OIDC `sub` 기반 사용자 조회·가입·로그인 service와 OAuth2 인증 성공·실패 handler를 확인함. 검증된 이메일만 허용하고 다른 인증 방식과의 자동 계정
  연결을 차단했으며, 기존 JWT·Refresh Token 발급 구조를 재사용함. handler 테스트 컴파일과 관련 테스트 성공을 확인하고 다음 작업을 Google client 설정과 Security filter
  chain 연결로 변경함.
- 2026-08-28: 만료 Refresh Token을 기본 1시간 주기로 1,000개씩 삭제하는 Scheduler와 MySQL Batch 삭제를 확인함. 동일 기준 시각 반복, 0건 멱등 처리, 실패 전파, 만료
  경계 포함 삭제, 유효 토큰 보존 테스트를 확인하고 Testcontainers MySQL 8.4 통합 테스트 2개 성공을 검증함. 다중 인스턴스는 별도 분산 락 없이 원자적 삭제를 사용하도록 결정하고 다음 작업을
  OAuth2 Google 로그인 흐름으로 변경함.
- 2026-08-28: Bearer Access Token 인증이 필요한 `/api/auth/logout-all` endpoint와 사용자 id 기준 Refresh Token 일괄 삭제를 확인함. 토큰이 없는
  사용자의 멱등 처리, 잘못된 JWT subject 거부, 비인증 접근 거부, 다른 사용자 토큰 보존을 포함한 단위·보안 테스트를 확인함. Testcontainers MySQL 8.4에서 전체 세션 폐기 통합
  테스트 4개 성공을 검증하고 다음 작업을 만료 Refresh Token 정리로 변경함.
- 2026-08-28: 인증 없이 호출 가능한 `/api/auth/logout` endpoint와 Refresh Token 해시 기반 개별 세션 폐기를 확인함. 존재하지 않는 토큰의 멱등 처리, 빈 토큰 검증,
  비인증 접근을 포함한 관련 테스트를 확인함. Testcontainers MySQL 8.4에서 로그아웃 삭제와 폐기 토큰 재발급 거부 통합 테스트 3개를 검증하고, 통합 테스트 클래스 간 종료된 container
  datasource 재사용 문제를 `@DirtiesContext(AFTER_CLASS)`로 해결함. 전체 테스트 50개 성공을 확인하고 다음 작업을 사용자 전체 세션 폐기와 만료 Refresh Token 정리로
  변경함.
- 2026-08-26: opaque Refresh Token 발급, SHA-256 해시 저장, 14일 만료, 비관적 잠금 기반 회전과 `/api/auth/refresh` endpoint 구현을 확인함.
  인증·보안·전체 테스트 성공을 확인하고 다음 작업을 로그아웃 및 Token 폐기 전략으로 변경함. Refresh Token Repository의 MySQL 전용 통합 테스트는 후속 보강 항목으로 남김.
- 2026-08-26: JWT 인증 사용자 조회 endpoint와 subject 검증, 사용자 미존재 오류 처리를 확인함. 사용자 service/controller 테스트와 전체 테스트 성공을 확인하고, 다음
  작업을 Refresh Token으로 변경함.
- 2026-08-26: Testcontainers 기반 MySQL 8.4 공통 테스트 환경과 Repository/Flyway 통합 테스트 3개를 확인함. 해당 통합 테스트 및 전체 테스트 성공을 확인하고, 다음
  작업을 인증된 사용자 조회 endpoint로 변경함.
- 2026-08-25: Spring Security filter chain 테스트 4개와 로그인 공개 matcher 수정을 확인하고, 해당 테스트 및 전체 테스트 성공을 확인함. 다음 작업을 Repository 및
  Flyway 통합 테스트 재설계로 변경함.
- 2026-08-25: 인증 테스트 메서드명을 영문으로 통일하고 한국어 `@DisplayName`을 추가한 뒤 전체 테스트 성공을 확인함.
- 2026-08-25: 회원가입·로그인 서비스 테스트 7개, JWT 테스트 1개, Controller 테스트 6개 및 프로젝트 전체 테스트 성공을 확인함. Controller와 JWT 테스트 단계를 완료하고
  Spring Security filter chain 검증을 다음 작업으로 변경함.
- 2026-08-25: 미구현 범위를 합의된 구현 순서로 변경하고 다음 작업을 Controller API 테스트로 명시함.
- 2026-08-25: 최초 현황 문서 작성. 현재 인증 API, 보안 구성, 사용자 도메인과 작성된 테스트 범위를 코드 및 Git 이력에서 정리함.
