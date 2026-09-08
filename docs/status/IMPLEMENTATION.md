# 구현 상세

[프로젝트 현황으로 돌아가기](../PROJECT_STATUS.md)

인증·사용자·문서 API와 실행 환경·DB 구조의 상세 참조다. 기업·채용공고는 [별도 문서](CATALOG.md), RAG 최신 구현 범위는 [검증 기록](TEST_RESULTS.md)을 참고한다.

## 프로젝트 개요

- Spring Boot 기반 Interview AI 백엔드
- Java 21
- Gradle Wrapper 9.5.1
- Spring Boot 4.1.0
- MySQL 8.4 및 Flyway
- 기본 프로필: `local`
- 기본 서버 포트: `8080`

## 현재 구현된 기능

### RAG Spring AI·Qdrant 외부 색인 — 구현·전체 회귀 검증 완료 (2026-09-08)

- Spring AI 2.0.1의 OpenAI embedding과 Qdrant VectorStore 의존성을 추가하고, 기본 비활성화 상태에서 환경변수로 embedding model·vector store·색인
  scheduler를 활성화한다.
- `RagTokenChunker`는 CL100K_BASE tokenizer로 700 token chunk와 100 token overlap을 생성하며 빈 본문과 최대 chunk 초과를 거부한다.
- `QdrantRagIndexProcessor`는 선점 attempt의 결정적 point ID와 source sequence·generation·공개 범위·소유자/기업 metadata를 사용해 batch
  UPSERT한다.
- DELETE는 원본 키 전체를 지우지 않고 `sourceType`, `sourceId`, `sourceSequence <= DELETE 순번` Qdrant filter로 이후 generation을 보호한다.
- 각 외부 batch와 DELETE 전후에 lease를 연장하며 소유권 상실·인터럽트·Qdrant 오류를 기존 worker 실패 처리로 전달한다.
- 조건부 `RagIndexJobScheduler`는 한 번에 설정된 최대 작업 수까지 소비하고 빈 큐, 처리 실패, lease 상실과 기반 오류를 구분한다. 기본 lease는 외부 호출 시간을 고려해 300초다.
- Docker Compose에 Qdrant 1.19.1과 영속 volume, HTTP 6333·gRPC 6334 포트를 추가했다.
- 설정·chunk·Processor·scheduler 20개와 기존 MySQL RAG 회귀를 포함한 RAG 236개, 프로젝트 전체 548개가 성공했다. 실패·오류·건너뜀은 0이며 실제 OpenAI·Qdrant
  네트워크 색인과 검색 서비스는 후속 검증 범위다.

### RAG 활성 generation·DELETE tombstone — 구현·검증 완료 (2026-09-08)

- V9은 원본 관리 행에 `active_generation_id`, `active_sequence`, `tombstone_sequence`와 일관성 CHECK를 추가한다. 기존 DELETE 작업이 있는 원본은
  상태와 관계없이 가장 큰 DELETE 순번을 tombstone으로 이관하며 기존 성공 작업으로 활성 generation을 추정하지 않는다.
- DELETE 등록은 순번 발급과 같은 원본 행 잠금 안에서 tombstone을 즉시 전진시키고 활성 generation을 제거한다. 작업 저장·원본 변경과 같은 호출자 트랜잭션에 참여하므로 실패 시 함께
  롤백된다.
- UPSERT 외부 쓰기는 매 선점 attempt UUID를 generation으로 사용하며 작업·attempt·chunk별 결정적 point UUID를 제공한다. 재선점된 실행은 이전 외부 쓰기와 다른
  generation·point를 사용한다.
- UPSERT 성공은 원본 관리 행을 잠근 뒤 현재 attempt와 lease 조건으로 작업 상태를 변경하고, 해당 순번이 원본의 최신 등록 순번이며 tombstone보다 최신일 때만 활성 generation을
  교체한다. 작업 성공과 활성화는 하나의 새 트랜잭션으로 커밋된다.
- 새 UPSERT가 처리 중이거나 실패하면 기존 활성 generation을 유지한다. 오래된 UPSERT 완료는 작업 자체가 성공하더라도 활성화되지 않고, 늦은 DELETE 완료는 이후 활성 generation을
  제거하지 않는다.
- 활성 generation 조회를 제공해 외부 검색 후보가 현재 검색 가능한 generation인지 확인할 수 있다. Processor·scheduler와 Qdrant 색인은 후속 단계에서 연결됐으며 검색 연결은
  남아 있다.
- 신규 37개를 포함한 RAG 216개와 전체 528개 테스트가 성공했고 실패·오류·건너뜀은 0이다.

### RAG 작업 실행 기반 — 구현·검증 완료 (2026-09-08)

- V8은 작업에 `attempt_id`, `lease_expires_at`, `available_at`, `failure_code`와 RUNNING lease CHECK·조회 인덱스를 추가한다. 기존 JPA 등록
  코드의 초기 PENDING 저장을 유지한다.
- 실행 전용 JDBC Repository는 `MANDATORY`, 실행 서비스는 `REQUIRES_NEW`·`READ_COMMITTED`를 사용한다. 선점은 `FOR UPDATE SKIP LOCKED`이며 변경마다
  JPA의 `lock_version`도 증가시킨다.
- lease·재시도 판단은 DB UTC 시각 기준이고, `updated_at`은 기존 값보다 후퇴하지 않는다. 외부 색인 연결 후 기본 lease는 300초, 실패 재시도 지연은 30초이며 둘 다 1~86400초
  설정 범위다.
- 매 선점마다 UUID attempt를 새로 발급하고 횟수를 증가시킨다. 완료·실패·연장은 현재 attempt와 유효 lease를 조건으로 갱신한다. 만료된 작업은 재선점하거나 시도 소진 시 FAILED로
  정리한다.
- 소진 작업 한 건을 정리한 호출은 빈 결과를 반환할 수 있다. 따라서 worker의 `NO_JOB`은 전체 큐가 완전히 비었다는 보장이 아니며 호출자는 다음 poll을 계속해야 한다.
- worker는 `NOT_SUPPORTED`로 외부 처리 중 트랜잭션을 유지하지 않는다. 후속 단계의 Qdrant Processor와 조건부 scheduler가 작업을 소비하며, 긴 작업의 lease 갱신은
  batch 전후 Processor 호출이고 별도 heartbeat thread는 아니다.
- 처리 예외는 고정 실패 코드로 저장하고 완료 저장 DB 오류는 전파한다. 같은 원본의 중복 실행은 가능하지만 generation·tombstone으로 오래된 결과의 검색 노출을 차단한다.
- 실행 서비스 실패 코드 정규식의 불필요한 닫는 대괄호를 수정했고 정상·100자 경계·잘못된 문자·길이 초과 테스트로 검증했다.
- 신규 실행 기반 단위·MySQL 통합 테스트 51개를 포함한 전체 491개가 성공했으며 실패·오류·건너뜀은 0이다.

### 자기소개서·이력서 RAG 작업 등록 연결 (2026-09-08)

- 자기소개서 생성·수정·과거 버전 복원은 ID와 현재 버전 확정 후 같은 쓰기 트랜잭션에서 UPSERT 작업을 등록한다.
- 자기소개서 삭제는 원본을 비관적으로 잠근 뒤 DELETE 작업을 먼저 등록하고 원본을 삭제한다. 등록 실패 시 원본 삭제를 진행하지 않는다.
- 이력서 생성·제목 수정·파일 교체는 `registerResumeChange`로 연결한다. 추출 완료 문서는 UPSERT, 추출 대기·실패 문서는 DELETE 작업으로 등록한다.
- 이력서 삭제는 비관적 잠금 조회와 DELETE 작업 등록 후 DB 원본을 삭제하며, PDF 파일 삭제는 트랜잭션 커밋 이후에만 실행한다.
- 대표 자기소개서·대표 이력서 설정은 RAG 원본 내용에 영향을 주지 않아 작업을 등록하지 않는다.
- 서비스 테스트 5개를 추가해 추출 실패 분기, 등록 순서와 등록 실패 시 미삭제를 보강했다. 선택 테스트 32개와 전체 440개가 성공했고 실패·오류·건너뜀은 0이다.

### 기업·채용공고 RAG 작업 등록 연결 (2026-09-08)

- `RagSourceChangeRegistrationService`가 스냅샷 생성과 `rag-v1`, 최대 3회 실행 정책을 공통화한다.
- 기업·채용공고 생성은 ID 확정 후, 수정은 잠근 원본 변경 직후, 삭제는 원본 삭제 전에 같은 쓰기 트랜잭션에서 UPSERT 또는 DELETE 작업을 등록한다.
- 작업 등록 실패 시 원본 변경도 함께 롤백되며 외부 색인 호출은 CRUD 트랜잭션에서 수행하지 않는다.
- 신규 단위 테스트 7개와 MVC 테스트 61개를 포함한 전체 435개가 성공했다. 실패·오류·건너뜀은 0이며 MySQL 원자성·동시성 회귀도 완료했다.
- 기업 삭제의 공고 존재 확인에는 비관적 읽기 잠금을 적용해 `REPEATABLE_READ`의 과거 일관 읽기 스냅샷 대신 최신 커밋 상태를 확인한다.
- 자기소개서·이력서 CRUD 연결은 같은 날짜의 후속 단계에서 완료했다.

### RAG 작업 등록 영속화 (2026-09-07)

- `V7__create_rag_index_jobs.sql`과 `RagIndexJobEntity`·`RagIndexJobRepository`·`RagIndexJobRegistrationService`로 작업 등록을
  저장한다.
- UPSERT는 원본 키·소유자/기업 ID·제목·본문·revision·pipeline version을 보존하고 DELETE는 원본 키만 저장한다. 문자열 스냅샷은 LONGTEXT, pipeline version은
  VARCHAR (100)이다.
- 초기 상태 PENDING, 최대 실행 횟수·시도 횟수 0, UTC 마이크로초 생성/수정 시각, 낙관적 잠금 버전을 저장한다. 작업 ID는 BIGINT 자동 생성이다.
- 원본 키·순번 unique, V6 원본 관리 행 FK, 작업 종류·상태·횟수·시각·본문 CHECK 제약을 적용한다. 실제 원본 테이블에는 FK를 두지 않는다.
- 등록 서비스는 MANDATORY로 호출자의 트랜잭션에 참여하고 기존 순번 서비스의 행 잠금과 순번 발급 이후 작업을 저장한다. 작업 INSERT 실패 및 호출자 롤백 시 순번·작업을 함께 롤백한다.
- 기존 CRUD 연결·원본 변경과 등록의 원자성 정책, 실행 상태 전이 저장·worker·lease·활성 generation·tombstone·외부 색인 연결은 후속 범위다. 기존 `RagIndexJob`은 메모리
  내 실행 모델로 유지한다.
- 신규 단위 6개·MySQL 통합 40개를 포함한 전체 428개 성공을 확인했다. 상세 근거는 [테스트 실행 기록](TEST_RESULTS.md)을 참고한다.

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
- Flyway `V1__create_users.sql`, `V2__create_refresh_tokens.sql`, `V3__create_cover_letters.sql` migration
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
    - `COVER_LETTER_NOT_FOUND`: HTTP 404
    - `COVER_LETTER_VERSION_NOT_FOUND`: HTTP 404
    - `REPRESENTATIVE_COVER_LETTER_NOT_FOUND`: HTTP 404
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

### 자기소개서 API

- 모든 endpoint는 Bearer Access Token 인증이 필요하며 JWT subject의 사용자 id를 기준으로 소유권을 검사한다.
- `POST /api/cover-letters`: 제목 100자 이하, 본문 20,000자 이하 validation 후 자기소개서와 초기 버전 1을 생성하고 HTTP 201 반환
- `GET /api/cover-letters`: 사용자의 자기소개서를 수정 시각 내림차순으로 조회하고 현재 버전 번호와 대표 여부 반환
- `GET /api/cover-letters/{coverLetterId}`: 소유한 자기소개서의 현재 버전 제목·본문과 대표 여부 조회
- `PUT /api/cover-letters/{coverLetterId}`: 기존 버전을 변경하지 않고 새 버전을 추가하며 비관적 잠금으로 동시 버전 번호 충돌 방지
- `DELETE /api/cover-letters/{coverLetterId}`: 자기소개서를 hard delete하고 버전 및 대표 설정을 cascade 삭제
- `GET /api/cover-letters/{coverLetterId}/versions`: 전체 버전을 버전 번호 내림차순으로 조회
- `GET /api/cover-letters/{coverLetterId}/versions/{versionNumber}`: 특정 버전 조회
- `POST /api/cover-letters/{coverLetterId}/versions/{versionNumber}/restore`: 과거 버전의 제목·본문을 새 버전으로 생성하여 복원
- `GET /api/cover-letters/representative`: 대표 자기소개서 조회, 미설정 시 HTTP 404 반환
- `PUT /api/cover-letters/{coverLetterId}/representative`: 대표 자기소개서를 새로 설정하거나 교체하고 HTTP 204 반환
- `DELETE /api/cover-letters/representative`: 대표 설정을 멱등하게 해제하고 HTTP 204 반환
- 다른 사용자의 자기소개서는 존재 여부가 노출되지 않도록 HTTP 404 `COVER_LETTER_NOT_FOUND`로 처리한다.
- PDF 업로드와 텍스트 추출은 파일 저장 정책 확정 이후 별도 범위로 유지한다.

### 이력서 API와 파일 저장

- 모든 endpoint는 Bearer Access Token 인증이 필요하며 JWT subject의 사용자 id를 기준으로 소유권을 검사한다.
- PDF 등록·목록·상세·다운로드·제목 수정·PDF 교체·삭제와 대표 이력서 조회·설정·교체·멱등 해제를 지원한다.
- 다른 사용자의 이력서는 존재 여부를 노출하지 않고 HTTP 404 `RESUME_NOT_FOUND`로 처리한다.
- PDF 확장자·Content-Type·시그니처와 PDFBox 파싱을 검증하고 빈 파일, 손상·암호화 PDF 및 10MB 초과 파일을 거부한다.
- DB에는 binary 대신 원본 파일명, 불투명 저장 키, 크기, SHA-256, 추출 텍스트와 `PENDING`·`COMPLETED`·`FAILED` 상태를 저장한다.
- 로컬 저장 키는 `{userId}/{UUID}.pdf`이며 경로 정규화 검사로 저장 루트 이탈을 차단한다.
- 신규 파일은 DB rollback 시 삭제하고 교체 전·삭제 대상 파일은 DB commit 후 삭제하며, 회원 탈퇴 시 모든 이력서 원본도 commit 후 정리한다.

### 데이터베이스 통합 테스트 기반

- Testcontainers 기반 MySQL 8.4 통합 테스트 환경
- Spring Boot `@ServiceConnection`을 통한 테스트 datasource 자동 연결
- Docker를 사용할 수 없는 환경에서는 통합 테스트 자동 비활성화
- 통합 테스트 클래스 종료 후 Spring Context를 폐기해 새 Testcontainer 주소를 사용하도록 구성
- 실제 MySQL에서 Flyway V1 migration 적용 여부 검증
- 실제 MySQL에서 Flyway V3 자기소개서 migration 적용 여부 검증
- `UserRepository`의 로컬 사용자 저장 및 이메일 조회 검증
- 이메일 unique constraint 위반 시 `DataIntegrityViolationException` 발생 검증
- 자기소개서 버전 unique, 사용자별 대표 문서 unique, 대표 문서 소유권 복합 외래 키와 cascade 삭제 검증

### 데이터베이스 ERD 기준

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
- `cover_letters`
    - 사용자 외래 키 `user_id`와 현재 제목, 현재 버전 번호 및 생성·수정 시각 저장
    - 사용자 삭제 시 자기소개서를 cascade 삭제
    - 수정과 복원의 버전 번호 할당 시 비관적 잠금 적용
- `cover_letter_versions`
    - 자기소개서별 immutable 제목·본문 snapshot과 1부터 시작하는 버전 번호 저장
    - `(cover_letter_id, version_number)` unique constraint 적용
    - 자기소개서 삭제 시 모든 버전 cascade 삭제
- `cover_letter_representatives`
    - `user_id`를 기본 키로 사용해 사용자당 대표 자기소개서 최대 1개 보장
    - `cover_letter_id` unique constraint로 하나의 자기소개서가 하나의 대표 설정에만 연결되도록 제한
    - `(user_id, cover_letter_id)` 복합 외래 키로 대표 자기소개서의 소유권 일치 보장
    - 자기소개서 또는 사용자 삭제 시 대표 설정 cascade 삭제
- `resumes`
    - 사용자 외래 키, 제목, 원본 파일명, 저장 키, Content-Type, 파일 크기, SHA-256와 추출 결과 저장
    - 저장 키 unique, `(user_id, id)` 복합 unique, 10MB 크기와 추출 상태 정합성 check constraint 적용
    - 사용자 삭제 시 이력서 metadata cascade 삭제
- `resume_representatives`
    - `user_id` 기본 키와 `(user_id, resume_id)` 복합 외래 키로 사용자당 대표 1개와 소유권 일치 보장
    - 이력서 또는 사용자 삭제 시 대표 설정 cascade 삭제
