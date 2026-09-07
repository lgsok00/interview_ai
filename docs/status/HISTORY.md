# 프로젝트 변경 이력

[프로젝트 현황으로 돌아가기](../PROJECT_STATUS.md)

## 2026-09-08 — 기업·채용공고 RAG 작업 등록 연결

- 후속 수정: `existsByCompanyId`에 비관적 읽기 잠금을 적용했다. 사용자 선택 재실행 2개와 전체 재실행 435개가 성공했고 실패·오류·건너뜀은 0이다.
- Colima 소켓을 명시한 전체 재실행에서 435개가 건너뜀 없이 실행되어 434개 성공, 1개 실패를 확인했다. 기업 삭제와 공고 생성 경합에서 일반 `exists` 조회가 MySQL
  `REPEATABLE_READ` 스냅샷 때문에 신규 공고를 놓치고 FK 위반으로 끝나는 문제이며 잠금 읽기 수정이 필요하다.
- 공용 `RagSourceChangeRegistrationService`를 추가하고 기업·채용공고 생성·수정·삭제가 같은 쓰기 트랜잭션에서 RAG UPSERT·DELETE 작업을 등록하도록 연결했다.
- 신규 단위 테스트 7개와 기존 MVC·동시성 테스트의 의존성 및 `saveAndFlush` 기대를 갱신했다.
- 사용자 선택 실행은 16초, 전체 실행은 9초에 BUILD SUCCESSFUL이었다. 전체 실행 후 XML 52개에서 403개 발견, 339개 성공, 실패·오류 0, 건너뜀 64를 확인했다.
- 신규 단위 테스트와 MVC 테스트는 성공했지만 MySQL/Testcontainers 통합 테스트가 건너뛰어져 원자성·동시성 회귀 검증은 대기 상태다.
- 확인한 HEAD는 `408ff7a`이며 구현·테스트·관련 문서는 커밋 대기 상태다.

## 2026-09-07 — RAG 작업 등록 영속화

- 후속 확인: 테스트 SQL 문자열 조합을 고정 SQL과 값 바인딩으로 변경하고 JPA 관리 `lockVersion`의 초기값·IDE 경고 억제를 명시했다. 최종 등록 서비스 MySQL 통합 테스트에서 40개
  성공, 실패·오류·건너뜀 0을 확인했다.
- V7 작업 테이블, 작업 엔티티·제한된 Repository·등록 서비스를 추가했다. UPSERT 스냅샷과 DELETE 원본 키를 초기 PENDING 상태로 저장하며 순번 발급과 같은 호출자 트랜잭션에 참여한다.
- 원본별 작업 순번 unique와 관리 행 FK를 적용했다. 등록 순번은 원본 revision의 최신 순서를 보장하지 않으며 메모리 실행 모델과 영속 등록 모델을 구분한다.
- 사용자 선택 실행 (41초)·전체 실행 (2분 19초) 성공. 최신 XML에서 전체 428개, RAG 121개 성공, 실패·오류·건너뜀 0을 확인했다. 신규 단위 6개·MySQL 통합 40개를 포함한다.
- 다음 단계는 원본 변경·삭제와 등록의 원자성·스냅샷 조회 시점·원본 잠금 정책 및 기존 CRUD 연결이다. 실행 상태 전이 저장·worker·lease·활성 generation·tombstone·외부 연결은 후속
  범위다.
- HEAD `84f81cb` 기준으로 이번 구현·테스트·관련 문서는 커밋 대기 상태다. 기존 문서 변경과 과거 기록을 보존했다.

## 2026-09-07 — RAG 원본별 순번 영속화·등록 직렬화 기반

- 후속 확인: SQL 범위·Executor 경고 수정 후 사용자 선택 실행에서 15개 성공·실패·오류·건너뜀 0을 확인했다. 구현과 경고 수정은 `84f81cb`로 커밋되었으며 확인 시 작업 트리는 깨끗했다. 아래
  커밋 대기 표현은 최초 문서 갱신 당시 기록이다.

- V6 원본 관리 테이블과 JPA 엔티티·제한된 Repository·순번 서비스를 추가했다. 기존 entity/repository/service 패키지 구조를 유지한다.
- 원본 키 unique·최초 생성 경합 처리·비관적 쓰기 잠금·호출자 트랜잭션 참여로 순번 발급 기반을 구현했다.
- 사용자 선택·전체 실행 성공. 최신 XML에서 전체 382개, RAG 75개 성공이며 신규 단위 2개·MySQL 통합 15개를 포함한다. 실패·오류·건너뜀은 모두 0이다.
- 다음 단계는 작업 본문·상태 저장과 순번 발급의 트랜잭션 연결이다. worker·lease·활성 generation·tombstone은 완료 범위에 포함하지 않는다.
- 확인한 HEAD는 `bbb951e`이며 이번 구현·테스트·문서는 커밋 대기 상태다. 아래 과거 기록의 기준점과 다음 작업 표현은 당시 상태로 보존한다.

아래 기록의 현재·다음 작업·작업 트리 표현은 기록 당시 기준이다. 최신 상태와 Git 기준점은 [프로젝트 현황](../PROJECT_STATUS.md)을 따른다.

## 문서 구조 변경

- 2026-09-07: 현황 문서를 현재 상태 중심으로 축약하고 구현 상세, 테스트 목록·실행 기록, 기업·채용공고 결정, 로드맵, 확인 사항과 변경 이력을 분리했다. 과거 기록은 보존하고 Git 기준점을
  48cabe7로 갱신했다. 애플리케이션 변경 및 테스트 재실행은 없다.

## 변경 이력

- 2026-09-07: Qdrant metadata와 메모리 내 색인·삭제 작업 수명주기 모델을 구현했다. generation별 point ID, 실행별 attempt 검증, 제한된 재시도·취소와 chunk 범위
  검증을 추가했다. 사용자 선택·전체 실행의 BUILD SUCCESSFUL 및 최신 XML의 전체 365개 성공 (RAG 58개 포함)·실패 0·오류 0·건너뜀 0을 확인했다. 다음 작업을 색인 작업 영속화·동시
  실행 제어로 변경했으며 실제 Qdrant 연결·worker 실행은 후속 범위로 유지한다.

- 2026-09-07: `RagSourceSnapshotFactoryTest`의 항상 같은 값을 받는 현재 자기소개서 버전 helper 매개변수 2개를 제거해 IDE 경고를 정리했다. 사용자 RAG 선택 테스트
  재실행의 BUILD SUCCESSFUL과 실제 XML 전체 33개 성공·실패 0·오류 0·건너뜀 0을 확인했다.

- 2026-09-07: 기업·채용공고·현재 자기소개서 버전·이력서 추출 상태를 RAG 스냅샷과 상태 결과로 변환하고, 인증 사용자 확인과 개인 문서 소유권 조건을 적용했다. 사용자 RAG 선택 테스트 실행의
  BUILD SUCCESSFUL과 실제 XML 전체 33개 성공·실패 0·오류 0·건너뜀 0을 확인했다. 전체 회귀 검증은 대기 상태이며 다음 작업을 Qdrant metadata와 색인 상태·작업 수명주기 설계로
  변경했다.

- 2026-09-07: `RagSourceDocumentTest`의 예외 검증 helper 3개를 반환 값이 없는 메서드로 변경해 IDE 경고를 제거했다. 사용자 재실행의 BUILD SUCCESSFUL과 실제
  XML 14개 성공·실패 0·오류 0·건너뜀 0을 확인했다.

- 2026-09-07: RAG의 `RagVisibility`, `RagSourceType`, `RagSourceKey`, `RagSourceSnapshot` 구현을 확인하고 정상·경계·실패 단위 테스트를 추가했다.
  사용자 선택 실행의 BUILD SUCCESSFUL과 실제 XML 14개 성공·실패 0·오류 0·건너뜀 0을 확인했다. 전체 회귀 검증은 대기 상태이며 다음 작업을 문서 유형별 변환기와 접근 제어 계약으로
  변경했다.

- 2026-09-07: 동시성 테스트의 nullable 언박싱·Executor 자원 관리·finally throw 경고를 정리하고 사용자 재실행 2개 성공·건너뜀 0을 확인했다. 별도 전역 예외 handler
  매개변수 제거는 MVC 재검증 대기로 구분했다.

- 2026-09-07: 사용자 전체 테스트 명령 `.\gradlew.bat test --console=plain`의 BUILD SUCCESSFUL과 실제 XML 41개의 전체 307개 성공·실패 0·오류 0·건너뜀
  0을 확인했다. 기업·채용공고 API 단계를 완료하고 다음 작업을 RAG 문서 모델·metadata·접근 제어 설계로 변경했다.

- 2026-09-07: 기업·채용공고 Controller·전역 예외 반영과 Location·관심 해제 호출 수정 확인. 테스트 4개 파일 추가 후 사용자 선택 실행에서 CHECK 제약 예외 기대값 오류 2개를
  확인해 보완했다. Repository 통합 테스트 17개 성공·건너뜀 0을 확인하고 전체 회귀 검증 대기로 기록했다.

- 2026-09-04: `CatalogException.errors` 필드와 private 생성자 매개변수의 `Map<String, String>` 수정 반영을 확인하여 현재 수정 대기 표시를 해제함.
  컴파일·테스트는 여전히 미확인으로 검증 대기를 유지하고 다음 작업을 요청·응답 DTO 작성으로 정리함.
- 2026-09-04: 공통 예외·입력 검증·UTC Clock·DB 사용자 및 관리자 역할 검사 코드 4개의 반영을 확인함. `CatalogException.errors`의 `Map<String, Object>`를
  `Map<String, String>`으로 보완할 필요가 있어 수정 대기로 기록함. 테스트·컴파일은 미확인이며 다음 작업은 타입 보완 확인과 요청·응답 DTO 작성이다. Git 기준점을 기업·채용공고
  모델·Repository 커밋 `e5f4f55`로 갱신함.
- 2026-09-04: 기업·채용공고를 관리자 수동 관리와 로그인 사용자 조회·관심 기업 범위로 정리함. V5, 엔티티 3개·enum 2개·Repository 3개의 코드 반영과 JobPosting 수정 시각·기업
  검색 userId 타입 보완을 확인함. 테스트·DB 적용은 미확인으로 `구현됨, 검증 대기`로 기록함. 임시 문서의 핵심 결정과 재개 지점을 이 문서에 통합하고 다음 작업을 4-1단계 DTO·검증·UTC
  Clock·관리자 권한 검사로 지정함.
- 2026-09-03: 이력서 관리를 사용자별 PDF 등록·조회·다운로드·제목 수정·파일 교체·삭제와 대표 설정 범위로 구현함. 로컬 원본 저장, 불투명 저장 키, PDFBox 텍스트 추출, SHA-256,
  10MB 제한, 추출 상태·실패 코드와 commit·rollback 기반 파일 정리를 적용함. Flyway V4에 이력서·대표 설정 테이블과 check·unique·소유권 복합 외래 키·cascade를 추가하고
  전체 205개 테스트 성공을 확인했으며 다음 작업을 기업 및 채용공고 관리 범위와 데이터 모델 확정으로 전환함.
- 2026-09-03: 자기소개서 관리 범위를 사용자별 CRUD, immutable 버전 이력·조회·복원과 대표 자기소개서 설정·교체·해제로 확정하고 구현함. Flyway V3에 자기소개서·버전·대표 설정 테이블과
  버전 unique, 대표 문서 unique, 소유권 복합 외래 키 및 cascade 제약을 추가함. 정상·경계·실패·소유권·DB 제약 테스트 32개를 추가하고 전체 테스트 178개 성공을 확인했으며 다음 작업을
  이력서 관리 범위와 파일 저장 정책 및 데이터 모델 확정으로 전환함.
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

## 분리 전 재개 메모 (2026-09-07 당시 원문)

과거 단계 설명과 당시 Git 기준점이 섞인 원문을 이력으로 보존한다. 현재 작업 지시로 사용하지 않는다.

## 다음 작업

운영 `prod` profile, DB·JWT·OAuth2 secret 주입, proxy header, graceful shutdown, health probe 설정과 자동 테스트를 완료했다. Java 21
multi-stage `Dockerfile`과 `.dockerignore`를 구현하고 `interview-ai-backend:local` 이미지 빌드, non-root 실행, MySQL 연결 및 실제 health
probe 응답까지 검증했다.

reverse proxy 환경의 OAuth2 HTTPS callback과 운영 ECS JSON 표준 출력 로그를 실제 운영 profile 컨테이너에서 검증했다. 로그 전 행에 서비스명과 배포 환경명이 포함되고, 검증
대상 JWT·OAuth2·DB secret 문자열은 포함되지 않는 것을 확인했다.

Refresh Token 정리 scheduler는 운영에서 전용 인스턴스 1개만 환경변수로 활성화하고 일반 API 인스턴스에서는 비활성화하기로 확정했다. 공통·운영 기본값은 비활성화하고 `local` profile은
기본 활성화하며, 설정 조건에 따른 Scheduler Bean 생성 여부를 자동 테스트로 검증했다.

Notion 프로젝트 문서를 기준으로 현재 구현을 대조한 결과, 인증 기반과 운영 실행 기반은 문서의 Spring Boot·Spring Security·OAuth2·JWT·MySQL·Docker 방향에 부합한다.
회원정보 수정, 로컬 사용자 비밀번호 변경과 회원 탈퇴를 현재 API 정책에 맞게 구현하고 검증했다. 자기소개서·이력서도 구현·검증했으며 기업·채용공고는 데이터 모델·Repository를 작성한 상태다. Spring
RAG 문서·접근 제어·metadata·메모리 내 색인 작업 모델은 구현·검증했다. AI·Qdrant 외부 연결과 실제 색인·검색은 아직 구현하지 않았다.

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

자기소개서 관리는 사용자별 작성·목록·상세·수정·삭제, immutable 버전 이력·조회·복원과 대표 자기소개서 설정·교체·해제 범위로 확정해 구현했다. Flyway V3는 자기소개서, 버전과 대표 설정 테이블을
추가하며 unique·복합 외래 키·cascade 제약으로 버전과 소유권 정합성을 보장한다. 관련 32개 테스트와 전체 178개 테스트의 성공을 확인했다.

이력서 관리는 사용자별 PDF 등록·조회·다운로드·제목 수정·파일 교체·삭제와 대표 설정 범위로 구현했다. Flyway V4는 파일 크기·추출 상태 check, 저장 키 unique, 소유권 복합 외래 키와
cascade 제약을 적용한다. 관련 테스트를 포함한 전체 205개 테스트 성공을 확인했다.

기업·채용공고 DTO·Service·Controller·전역 예외와 테스트가 반영되었고 전체 307개 테스트 성공·실패 0·오류 0·건너뜀 0을 확인했다. RAG 기반 구축에서 네 문서 유형의 공개 범위·원본
키·스냅샷 불변 모델과 유형별 변환기·접근 제어 서비스를 추가했다. Qdrant metadata와 메모리 내 색인·삭제 작업, 재시도·취소·attempt 검증 모델도 구현했다. RAG 58개를 포함한 전체 365개
성공·실패 0·오류 0·건너뜀 0을 확인했다. 다음 작업은 새 Flyway migration과 JPA 기반 색인 작업 영속화 및 원본별 동시 실행 제어다. 작업 선점·lease, 활성 generation 교체와 삭제
tombstone을 구체화한 뒤 Spring AI·Qdrant 연결과 실제 색인·검색으로 진행한다. 자기소개서 PDF 업로드는 이력서의 파일 저장·검증·추출 기반을 공통화하는 별도 후속 작업으로 유지한다.

실제 배포 환경 선정과 배포 플랫폼별 구성은 자기소개서·이력서, 기업·채용공고, RAG 질문 생성, 면접 답변 평가와 결과 조회로 이어지는 MVP 핵심 흐름이 완성된 뒤 진행한다.

## Git 기준점

- 기준 브랜치: `main`
- 기준 커밋: `d2e95ab feat: RAG 문서 변환과 접근 제어 추가`
- RAG 문서 모델·변환기·접근 제어 서비스와 기존 테스트는 커밋되어 있다. 현재 작업 트리에는 신규 metadata·색인 작업 모델 6개, 테스트 3개와 현황 문서 변경이 있다.
- 임시 설계·구현 가이드 두 파일은 삭제된 것을 확인했다. 필요한 결정과 재개 지점은 이 문서에서 유지한다.
