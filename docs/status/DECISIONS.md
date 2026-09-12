# 주요 결정과 확인 사항

[프로젝트 현황으로 돌아가기](../PROJECT_STATUS.md)

API·인증·운영 정책과 남아 있는 확인 사항을 관리한다. 기업·채용공고의 상세 결정은 [기업·채용공고 문서](CATALOG.md)를 참고한다.

## 알려진 확인 사항

- 면접 세션 생성 API는 `jobPostingId`만 필수로 받고 인증 사용자의 대표 자기소개서·대표 이력서를 서버에서 자동 선택한다. 대표 미설정은 오류 없이 선택 생략으로 처리한다.
- 대표 자기소개서는 생성 시점의 현재 버전을 사용한다. 대표 이력서는 추출 완료 상태와 비어 있지 않은 추출 본문을 요구하며, 준비되지 않았으면 HTTP 409
  `REPRESENTATIVE_RESUME_NOT_READY`로 세션 생성을 거부한다.
- 면접 생성 당시의 기업명·공고 제목/직무/본문과 선택 개인 문서 제목/본문을 세션 스냅샷으로 저장한다. 원본 수정·삭제 이후에도 과거 면접을 재현하기 위해 원본 ID에는 FK를 두지 않는다.
- 세션은 사용자 삭제 시, 질문은 세션 삭제 시 cascade 삭제한다. 세션별 질문 순서는 1 이상이며 unique로 보장한다.
- 세션 상태는 `GENERATING → READY → IN_PROGRESS → COMPLETED`를 정상 경로로 사용한다. 생성 중 실패는 `FAILED`와 실패 코드를 저장하고 재시도 시 `GENERATING`
  으로 복귀한다.
- 질문 유형은 기술·인성·꼬리 질문, 생성 출처는 AI·fallback을 구분하고 생성에 사용한 RAG context는 선택적 스냅샷으로 보존한다.
- RAG 원본 관리 코드는 기존 `rag.entity`·`rag.repository`·`rag.service` 구조를 따른다. Repository는 생성·잠금 연산만 노출하도록 Spring Data
  `Repository`를 상속한다.
- 원본 관리 행은 `(source_type, source_id)` unique로 식별하고 원본 테이블 외래 키를 두지 않는다. 원본 삭제 후에도 DELETE tombstone과 순번 기준으로 유지한다.
- RAG 순번은 DB 등록 직렬화 순서이며 원본 revision의 최신 순서를 보장하지 않는다. 순번 서비스와 등록 서비스 모두 `MANDATORY`로 호출자의 쓰기 트랜잭션에 참여하며 작업 저장과 함께
  커밋·롤백한다.
- V7 작업은 `(source_type, source_id, source_sequence)` unique와 원본 관리 행 FK를 사용한다. 실제 원본 테이블에는 FK가 없어 원본 없는 DELETE 등록이 가능하다.
- 영속 작업 ID는 Long이며 기존 메모리 `RagIndexJob`의 UUID·실행 시도 UUID와 구분한다. V7 등록 범위는 UPSERT 스냅샷·pipeline version 또는 DELETE 키와 초기
  PENDING·시도 횟수·생성/수정 시각이다. V8 실행 상태 전이는 별도 JDBC Repository가 관리하며 메모리 실행 모델로 복원하지 않는다.
- 등록 시 전달받은 불변 스냅샷을 LONGTEXT로 보존하고 기존 UTC clock의 시각을 마이크로초로 저장한다.
- 기업·채용공고 CRUD는 원본 생성 시 ID 확정 후, 수정 시 비관적 잠금 상태의 변경 직후, 삭제 시 원본 삭제 전에 같은 트랜잭션으로 작업을 등록한다. 파이프라인은 `rag-v1`, 최대 실행 횟수는
  3이다.
- 기업 삭제의 공고 존재 확인은 비관적 읽기 잠금을 사용한다. MySQL `REPEATABLE_READ`에서 관리자 조회가 만든 과거 스냅샷으로 동시 생성 공고를 놓치지 않고 최신 커밋 상태를 확인하기 위한
  정책이다.
- 자기소개서는 생성·수정·복원 시 현재 버전 UPSERT, 삭제 시 비관적 잠금 후 DELETE를 등록한다. 이력서는 생성·제목 수정·파일 교체 시 추출 상태에 따라 UPSERT 또는 DELETE를 등록하고, 삭제
  시 비관적 잠금 후 DELETE를 등록한다. 모두 원본 변경과 같은 쓰기 트랜잭션에 참여한다.
- 대표 문서 설정·해제는 원본 스냅샷 내용이 바뀌지 않아 RAG 작업을 등록하지 않는다.
- 회원 탈퇴는 회원 행을 먼저 잠그고 자기소개서·이력서를 ID 순서로 잠근 뒤 각 원본의 RAG DELETE를 같은 트랜잭션에서 모두 등록한다. 이후 회원 삭제의 DB cascade와 이력서 파일의 커밋 후 정리를
  수행하며, 등록 실패 시 회원·문서·파일을 유지한다.
- worker 선점·lease는 V8·JDBC 실행 Repository·독립 트랜잭션 서비스·단건 worker로 구현·검증했다. 실행 서비스 실패 코드 정규식도 수정하고 경계 테스트로 검증했다.
- 실행 전용 컬럼은 JDBC Repository가 관리하며 기존 JPA 엔티티에 매핑하지 않는다. JDBC 변경도 `lock_version`을 증가시킨다. lease와 재시도 시각은 DB UTC 기준으로 판단한다.
- 실행 attempt UUID를 외부 generation으로 사용하고 작업·attempt·chunk 조합으로 point UUID를 결정한다. 같은 작업 재선점도 새 generation을 사용해 만료된 외부 요청과
  분리한다.
- DELETE 등록은 호출자 트랜잭션에서 tombstone을 즉시 전진시키고 활성 generation을 제거한다. 이후 늦게 끝난 UPSERT는 최신 등록 순번이 아니므로 활성화되지 않는다.
- UPSERT 성공은 유효 attempt·lease와 원본의 최신 순번을 함께 확인하며 작업 성공과 활성 generation 교체를 원자적으로 커밋한다. 새 작업 처리 중에는 기존 활성 generation을
  유지한다.
- DELETE Processor의 외부 정리는 해당 DELETE 순번 이하만 대상으로 해야 한다. 늦은 DELETE 완료가 이후 UPSERT generation을 제거하지 않도록 원본 키 전체 삭제는 사용하지
  않는다.
- Spring AI Qdrant가 Java `Long` metadata를 문자열 payload로 저장하므로 `sourceId`, `ownerUserId`, `companyId`, `jobPostingId`와 관련
  동등 비교 필터는 문자열로 통일한다. `sourceSequence`는 정확한 문자열 값을 보존하고 범위 DELETE에는 숫자 `sourceSequenceOrder`를 사용한다.
- 외부 색인은 Spring AI 2.0.1·OpenAI embedding·Qdrant를 사용한다. CL100K_BASE 기준 700 token·100 token overlap, batch 전후 lease 연장,
  조건부 scheduler와 실행당 작업 한도를 적용한다.
- 외부 검색은 Qdrant에서 인증 사용자 공용 또는 현재 사용자 소유 metadata로 후보 50개를 제한하고, DB 활성 generation과 실제 원본 존재·소유권을 다시 확인한 뒤 순서를 유지해 최대 5개를
  반환한다. 구현·RAG 선택 검증과 실제 OpenAI·Qdrant 네트워크 smoke test를 완료했다.

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
- API base path는 현재 구현인 `/api`를 유지하며 URL 기반 버전 (`/api/v1`)은 도입하지 않는다. 외부 공개나 독립 배포 클라이언트 도입 전에 버전 정책을 다시 검토한다.
- Access Token 재발급 URI는 현재 구현인 `/api/auth/refresh`를 유지하고 Notion 명세의 `/auth/reissue`를 사용하지 않는다.
- 성공 응답은 공통 envelope 없이 endpoint별 DTO를 직접 반환하고, 목록·페이징 응답은 도메인별 전용 DTO로 정의한다. HTTP 204 응답은 body 없이 유지하며 오류 응답은 기존
  `GlobalExceptionHandler`와 `ErrorResponse` 형식을 유지한다.
- Notion 회원가입 명세의 `name`은 실제 구현의 `nickname`과 다르다.
- Notion ERD의 User에는 실제 스키마의 `provider_id`와 `refresh_tokens`가 빠져 있고 비밀번호 컬럼명도 실제 `password_hash`와 다르다.
- 기업·채용공고 API와 DB 관리자 권한 검사는 구현·검증을 완료했다. Spring AI·Qdrant·OpenAI embedding의 외부 색인과 검색 코드는 구현·선택 검증됐고 실제 네트워크 연동,
  면접·평가·성장 모듈은 아직 완료되지 않았다.
- 자기소개서 PDF 업로드와 텍스트 추출은 확정된 이력서 파일 정책을 공통화하는 후속 범위로 유지한다.
- RAG 설계의 `companyId AND jobPostingId AND userId` 조건은 기업 공용 문서와 사용자 전용 문서의 metadata가 다르므로 문서 유형별 필터 조합으로 구체화해야 한다.
