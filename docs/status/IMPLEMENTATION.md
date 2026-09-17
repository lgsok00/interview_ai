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

### 관리자 사용자 관리 — 정지·강제 삭제 포함 구현·자동 검증 완료 (2026-09-17)

- `GET /api/admin/users`: 이메일·닉네임 부분 검색, role/provider 필터, 기본 page 0·size 20(최대 100), `createdAt DESC, id DESC` 정렬. 검색어는
  trim 후 최대 100자이며 LIKE 특수문자를 escape한다.
- `GET /api/admin/users/{userId}`: 사용자 상세. 응답은 id·email·nickname·provider·role·createdAt·updatedAt이고
  passwordHash·providerId는 제외한다.
- `PATCH /api/admin/users/{userId}/role`: 필수 role(USER/ADMIN), 동일 역할은 멱등 반환, 성공은 200과 사용자 DTO다. Refresh Token 폐기는 추가하지
  않았다.
- DB ADMIN 검사 후 전체 관리자 행을 ID순 쓰기 잠금하고 결과에 호출자 ID가 남아 있는지 확인한다. 잠금 대기 중 강등된 호출자는 403 FORBIDDEN이다. 대상이 관리자 목록에 없으면 대상 행을
  별도로 잠근다.
- V17로 `ACTIVE`·`SUSPENDED` 상태와 정지 시각, 상태·시각 정합성 CHECK 및 상태·역할·ID 인덱스를 추가했다. 목록은 상태 필터를 지원하고 상세 응답에 상태·정지 시각을 포함한다.
- `PATCH /api/admin/users/{userId}/status`는 정지·복구를 멱등 처리한다. 정지는 Refresh Token을 전부 폐기하며 자기 정지와 마지막 활성 관리자 제거를 막는다.
- `DELETE /api/admin/users/{userId}`는 자기 강제 삭제를 막고 Refresh Token 폐기 후 회원 탈퇴와 같은 공통 삭제 서비스를 사용한다. 개인 문서 RAG DELETE 등록, DB
  cascade와 이력서 파일의 커밋 후 정리를 유지한다.
- 로컬·Google·GitHub 로그인과 Refresh Token 발급·회전을 차단하며, JWT 인증 뒤 DB 상태 필터가 정지 전 발급된 Access Token과 삭제 사용자의 토큰도 각각 403·401로
  거부한다.
- 자기 강등은 409 ADMIN_SELF_DEMOTION_NOT_ALLOWED이며 마지막 관리자 검사보다 우선한다. 회원 탈퇴도 전체 관리자 행을 먼저 잠그고 마지막 활성 관리자의 탈퇴를 막는다.
- 기존 User 역할 컬럼을 사용하며 신규 migration은 없다. 기존 CatalogException·GlobalExceptionHandler·ErrorResponse를 재사용한다.
- 관리자·사용자·인증·보안 선택 161개와 전체 934개가 성공했다. 실제 MySQL 검색·필터·LIKE escape·페이지 정렬, 관리자 잠금 직렬화와 동시 상호 강등, RAG 삭제 회귀를 포함한다.

### 면접 결과와 성장 분석 — 구현·자동 검증 완료 (2026-09-16)

- V15는 면접 세션에 nullable `completed_at`과 사용자·상태·완료 시각 인덱스를 추가하고 기존 완료 세션은 `updated_at`으로 backfill한다. 완료 상태와 완료 시각의 정합성을
  CHECK로 보장하며 세션 상세·목록 응답에도 완료 시각을 제공한다.
- `GET /api/interview-sessions/{sessionId}/result`는 인증 사용자 소유의 완료 세션만 조회한다. 타인 세션은 404로 은닉하고 미완료 세션은 409
  `INTERVIEW_RESULT_NOT_READY`다. 질문·답변·평가 원문과 전체·질문 유형별 STAR/논리성/직무 적합성 평균을 반환한다.
- 통계에는 `COMPLETED` 평가만 포함하고 대기·처리·실패·미요청 개수를 별도로 제공한다. 평가 완료 범위에 따라 `PENDING`, `PARTIAL`, `COMPLETED` 분석 상태를 반환하며
  평균은 소수점 첫째 자리까지 반올림한다.
- `GET /api/interview-growth-analysis`는 기본 90일, 최대 365일 범위에서 완료 세션의 완료 평가를 집계한다. 직무 스냅샷·기업 ID·공고 ID 필터를 선택적으로 적용하고 평가
  완료가
  없는 완료 세션은 제외 개수로 제공한다.
- 기간 전체 점수는 답변 수로 가중하고 세션 추이는 완료 시각 순서다. 최근 최대 3개 세션과 그 직전 최대 3개를 세션별 동일 비중으로 비교하며 두 세션부터 변화량을 제공한다.
- 질문 유형·평가 차원 조합의 표본이 3개 이상일 때 상·하위 강점과 약점을 제공한다. 80점 미만 약점은 점수 구간별 목표와 STAR·논리성·직무 적합성별 고정 행동을 가진 최대 3개 학습
  로드맵으로 변환한다. 별도 AI 호출이나 분석 결과 테이블은 사용하지 않는다.
- 서비스 6개·컨트롤러 4개 신규 테스트와 기존 세션 완료 시각 회귀를 포함해 전체 XML 93개·803개가 성공했고 실패·오류·건너뜀은 0이다.

### 면접 답변 저장·조회와 답변 기반 꼬리 질문 — 구현·자동 검증 완료 (2026-09-15)

- V13은 질문당 하나의 `interview_answers`와 질문의 nullable `parent_question_id`를 추가한다. 답변은 질문 FK cascade, 부모 연결은 self FK
  cascade·unique로 보호한다. 초기 질문은 부모 ID가 없다.
- `PUT /api/interview-sessions/{sessionId}/questions/{questionId}/answer`는 앞뒤 공백 제거 후 Java String 길이 1~10,000의 답변을 제출한다.
  새 제출은 IN_PROGRESS에서만 가능하고 수정은 허용하지 않는다. 같은 내용 재전송은 기존 결과, 다른 내용은 409 `INTERVIEW_ANSWER_CONFLICT`다.
- `GET /api/interview-sessions/{sessionId}/answers`는 소유자의 답변을 질문 순서로 반환하며 `followUpQuestionId`로 연결을 제공한다.
- `POST /api/interview-sessions/{sessionId}/questions/{questionId}/follow-up`는 저장된 답변으로 초기 질문당 최대 하나의 꼬리 질문을 동기 생성한다. 세
  API의 성공 응답은 200이다. 질문 순번은 기존 최대값 다음이며 꼬리 질문에 대한 답변은 가능하지만 추가 깊이는 금지한다.
- 입력은 직무 스냅샷·부모 질문·답변이며 추가 RAG 검색은 하지 않는다. 기존 mode/model과 전용 Chat Bean을 사용한다. FALLBACK_ONLY는 답변 일부를 인용하는 정형 질문, AI는 JSON
  단일 질문·20~1,000 code point·부모 질문 정규화 중복 검사를 적용한다.
- 외부 호출은 DB 트랜잭션 밖에서 실행한다. 기존 deadline의 동시 호출 제한과 45초 제한을 공유하며 SDK 재시도는 0이다. 생성 실패는 503
  `INTERVIEW_FOLLOW_UP_UNAVAILABLE`이고 답변은 보존되어 같은 API로 재시도할 수 있다. 자동 재시도 작업은 없다.
- 세션 잠금 뒤 답변·기존 꼬리 질문·마지막 순번도 비관적 잠금 읽기로 조회하여 MySQL 반복 읽기의 오래된 스냅샷을 피한다. 저장 시 소유권·상태·기존 질문을 재검사한다. DB 오류는 생성 실패로 변환하지
  않는다.
- 완료된 세션에서도 동일 요청의 기존 결과 재조회는 허용한다. 조기 완료는 유지하며 생성 도중 완료된 경우 새 질문 저장은 차단한다. 외부 AI 중복 호출 방지는 보장하지 않으며 중복 저장만 막는다.
- 실제 입력 JSON은 내부 context로 보존하고 응답에는 노출하지 않는다. 전체 753개·면접 181개·신규 49개 자동 검증을 완료했고 2026-09-16 실제 Chat smoke에서 AI
  꼬리 질문 생성을 확인했다.

### 면접 조회·진행·질문 제공 API — 구현·검증 완료 (2026-09-15)

- `GET /api/interview-sessions`는 인증 사용자의 세션만 생성 시각·ID 역순으로 페이징하며 큰 원본 본문을 제외한 요약 DTO를 반환한다.
  `GET /api/interview-sessions/{sessionId}`는 생성 시점 전체 스냅샷을 반환한다.
- `GET /api/interview-sessions/{sessionId}/questions`는 `READY`, `IN_PROGRESS`, `COMPLETED` 세션의 질문을 순번대로 반환한다. 질문 유형·생성
  출처는 제공하되 내부 RAG `contextSnapshot`은 노출하지 않는다.
- `POST /api/interview-sessions/{sessionId}/start`는 `READY → IN_PROGRESS`,
  `POST /api/interview-sessions/{sessionId}/complete`는 `IN_PROGRESS → COMPLETED`만 허용하며 소유자 조건의 비관적 잠금으로 동시 요청을 직렬화한다.
- `POST /api/interview-sessions/{sessionId}/generation/retry`는 기존 FAILED 작업의 수동 재시도를 접수하고 HTTP 202를 반환한다. 소유자·질문 없음·최대
  2회 정책은 기존 실행 서비스가 원자적으로 검사한다.
- 타인 세션은 `INTERVIEW_SESSION_NOT_FOUND`로 은닉하고 잘못된 상태의 질문 조회·전이는 `INTERVIEW_SESSION_CONFLICT`로 반환한다.
- 면접 선택 실행과 전체 회귀 실행이 성공했다. 전체 XML 79개에서 704개, 면접 XML 14개에서 132개가 성공했으며 실패·오류·건너뜀은 0이다.

### 초기 질문 생성·fallback·실행 정책 — 구현·자동 검증 완료 (2026-09-14)

- V12 `interview_generation_jobs`는 세션 ID를 PK/cascade FK로 사용한다. 세션 생성과 작업 등록은 같은 트랜잭션이며 모드·모델·`interview-v1`·attempt·수동
  재시도 횟수·lease·실패/fallback 사유를 저장한다.
- migration은 질문 없는 기존 `GENERATING` 세션을 `FALLBACK_ONLY` 작업으로 이관한다. 기존 데이터 backfill의 별도 업그레이드 시나리오 테스트는 아직 없다.
- JDBC 실행 서비스가 세션 → 작업 순서로 잠그고 DB UTC 기준으로 선점한다. 120초 lease와 UUID attempt를 사용하며, 질문 5개·작업 성공·세션 READY를 원자적으로 저장한다. 늦은
  응답·중복 완료는 저장하지 않는다.
- 초기 질문은 기술 3개·인성 2개다. JSON 구조·순서·유형·20~1,000 code point·정규화 중복을 검사한다. 문서별 입력 token 제한과 전체 12,000 token 검사, 최대 5개 RAG
  자료를 사용하고 실제 전송 입력 JSON을 질문 context로 보존한다.
- Chat 전용 모델은 SDK 재시도 0·45초 timeout·출력 최대 3,000 token·store=false로 구성한다. RAG 대기 15초·Chat 대기 45초 deadline과 동시 외부 호출 4개
  제한을 적용한다.
- 빈 검색은 즉시 고정 질문 fallback이다. 일시적 오류와 잘못된 출력은 최대 3회 실행하며 5초/20초 + 0~2초 jitter로 재예약한다. 소진 또는 마지막 lease 만료는 fallback으로
  복구한다.
- 인증·quota·설정·내부 오류는 FAILED, DB 오류는 전파/롤백한다. fallback은 READY이며 질문 출처와 작업의 fallback 사유로 구분한다. 수동 재시도는 소유자·FAILED·기존 질문 없음
  조건에서 최대 2회이며 2026-09-15 HTTP endpoint 연결과 검증을 완료했다.
- `interview.generation.enabled=false`, `mode=FALLBACK_ONLY`가 기본이다. `AI`는 model·API key·RAG 활성화가 필요하다.
  `spring.ai.model.chat=none`으로 기본 Chat 자동 구성을 끄고 전용 Bean을 사용한다.
- 전체 683개·면접 111개 자동 테스트 성공. 2026-09-16 실제 OpenAI Chat·Qdrant RAG smoke에서 AI 초기 질문 5개 생성을 확인했다. 의미상 유사 질문 차단과 생성
  POST 자체의 Idempotency-Key는 보장하지 않는다.

### 면접 세션 전용 내부 RAG 검색 — 구현·검증 완료 (2026-09-14)

- V11은 기존 공고에서 기업 ID를 backfill한 뒤 `interview_sessions.company_id`를 `NOT NULL`로 저장한다. 원본 수명주기와 면접 이력을 분리하기 위해 기업 FK는 두지
  않는다.
- 세션 생성 시 기업 ID를 생성 시점 원본 ID로 함께 보존한다. 대표 개인 문서가 없으면 해당 원본 ID와 검색 범위를 생략한다.
- `RagSearchScope`는 영속 사용자와 비어 있지 않은 정확한 원본 키 집합을 불변 복사하며, `InterviewRagSearchService`가 세션의 기업·공고·선택 자기소개서·선택 이력서로 범위를
  조립한다.
- 내부 검색은 정확한 `(sourceType, sourceId)` 문자열 metadata 조건으로 후보 50개를 요청한다. 반환 후보도 허용 집합, 활성 generation, 현재 원본 존재·개인 문서 소유권
  순서로 다시 검사해 최대 5개를 반환한다.
- 검색 결과 없음은 정상적인 빈 목록이며 Vector Store 장애와 손상 metadata는 질문 생성 계층이 재시도·fallback 여부를 결정할 수 있도록 전파한다. 외부 검색 API는 변경하지 않았다.
- 사용자 RAG·면접 선택 실행과 전체 회귀 실행이 성공했다. 전체 XML 72개에서 618개, RAG·면접 XML 31개에서 304개가 성공했으며 실패·오류·건너뜀은 0이다.

### 면접 세션 생성 API와 영속 기반 — 구현·검증 완료 (2026-09-12)

- V10은 `interview_sessions`, `interview_questions`를 추가한다. 세션은 사용자 FK를 사용해 회원 탈퇴 시 삭제되고 질문은 세션 삭제 시 함께 cascade 삭제된다.
- `POST /api/interview-sessions`는 JWT 인증과 양수 `jobPostingId`를 요구하며 생성된 `GENERATING` 세션의 전체 스냅샷을 HTTP 201로 반환한다.
- 대표 자기소개서·대표 이력서는 서버가 인증 사용자 기준으로 자동 선택한다. 대표 미설정은 정상적인 선택 생략으로 처리해 관련 세션 필드를 모두 `null`로 저장한다.
- 자기소개서는 대표 문서의 현재 버전 제목·본문을 사용한다. 이력서는 `COMPLETED` 추출 상태와 비어 있지 않은 추출 본문을 요구하며, 준비되지 않은 대표 이력서는 HTTP 409
  `REPRESENTATIVE_RESUME_NOT_READY`로 거부한다.
- 기업·공고·개인 문서의 ID와 생성 시점 제목·본문을 세션에 저장해 원본 수정·삭제 이후에도 당시 면접 입력을 보존한다.
- 공용·개인 원본 ID에는 FK를 두지 않는다. 원본 수명주기와 면접 이력을 분리하면서 ID는 추적용으로 유지한다.
- 세션은 `GENERATING`, `READY`, `IN_PROGRESS`, `COMPLETED`, `FAILED` 상태와 허용된 전이, 생성 실패 코드와 재시도를 제공한다.
- 질문은 세션별 1 이상의 순번을 unique로 보장하고 `TECHNICAL`, `BEHAVIORAL`, `FOLLOW_UP` 유형, `AI`, `FALLBACK` 생성 출처와 선택적인 RAG context
  스냅샷을 저장한다.
- 엔티티 단위 19개, MySQL 통합 6개, 생성 API·서비스·스냅샷 조립 14개가 성공했다. 면접 XML 6개에서 39개, 전체 XML 70개에서 603개 성공했으며 실패·오류·건너뜀은 0이다.
- 당시 다음 단계였던 Chat Model 질문 생성·fallback·재시도·중복 방지는 2026-09-14 구현·자동 검증을 완료했다. 최신 범위는 위 초기 질문 생성 항목을 참고한다.

### RAG 관리자 운영 API — 구현·검증 완료 (2026-09-17)

- `GET /api/admin/rag/sources`, `GET /api/admin/rag/sources/{sourceType}/{sourceId}`로 원본 관리 행을 조회한다.
- `GET /api/admin/rag/jobs`, `GET /api/admin/rag/jobs/{jobId}`로 작업 상태·순번·시도 횟수·실패 코드와 실행 시각을 조회한다. 개인 문서
  제목·본문·revision·저장 키·소유자·attempt 정보는 반환하지 않는다.
- `POST /api/admin/rag/jobs/{jobId}/retry`는 FAILED 작업의 같은 순번·스냅샷을 유지하고 실행 기회를 추가해 202를 반환한다. 최신 순번이 아닌 UPSERT와 수동 재시도 2회
  초과는 409다.
- `POST /api/admin/rag/sources/{sourceType}/{sourceId}/reindex`는 현재 원본을 잠그고 최신 스냅샷의 새 순번 UPSERT를 등록해 202를 반환한다. 원본 미존재는
  404, 이력서 본문 미준비는 409다.
- V16은 `manual_retry_count`와 0~2 범위 제약을 추가한다. 관리자 RAG 80개와 전체 916개 자동 회귀가 성공했다.

### RAG Qdrant 검색 — 실제 외부 연동 검증 완료 (2026-09-11)

- `GET /api/rag/search?query=`는 JWT subject의 실제 사용자를 확인하고 앞뒤 공백을 제거한 1~100자 검색어로 Qdrant 후보 50개를 조회한다.
- Qdrant 조회 단계에서 인증 사용자 공용 문서 또는 현재 사용자 소유 개인 문서로 metadata를 제한한다.
- 후보는 DB의 활성 generation과 tombstone 조건을 통과해야 하며, 반환 직전에 기업·채용공고 존재 여부와 자기소개서·이력서 소유권을 실제 원본 Repository로 다시 확인한다.
- Qdrant 유사도 순서를 유지하면서 접근 가능한 활성 결과를 최대 5개 반환한다. 검색 기능은 `rag.search.enabled`로 조건부 활성화하며 설정 metadata용 전용 properties 구성을
  둔다.
- 신규 검색 서비스 7개·컨트롤러 4개와 접근 제어 3개를 추가했다. RAG 선택 실행에서 250개, 프로젝트 전체 실행의 XML 64개에서 562개 성공했으며 실패·오류·건너뜀은 0이다.
- 실제 Qdrant가 Java `Long` metadata를 문자열 payload로 저장하는 동작에 맞춰 `ownerUserId` 검색 필터와 식별자 metadata를 문자열로 통일했다. 2026-09-11 실제
  OpenAI embedding·Qdrant 개인 문서 검색 smoke test를 완료했다.

### RAG Spring AI·Qdrant 외부 색인 — 구현·전체 회귀 검증 완료 (2026-09-08)

- Spring AI 2.0.1의 OpenAI embedding과 Qdrant VectorStore 의존성을 추가하고, 기본 비활성화 상태에서 환경변수로 embedding model·vector store·색인
  scheduler를 활성화한다.
- `RagTokenChunker`는 CL100K_BASE tokenizer로 700 token chunk와 100 token overlap을 생성하며 빈 본문과 최대 chunk 초과를 거부한다.
- `QdrantRagIndexProcessor`는 선점 attempt의 결정적 point ID와 source sequence·generation·공개 범위·소유자/기업 metadata를 사용해 batch
  UPSERT한다.
- Qdrant 식별자 metadata는 문자열로 저장하고, 정확한 순번 문자열과 DELETE 범위 비교용 숫자 `sourceSequenceOrder`를 함께 저장한다.
- DELETE는 원본 키 전체를 지우지 않고 `sourceType`, `sourceId`, `sourceSequence <= DELETE 순번` Qdrant filter로 이후 generation을 보호한다.
- 각 외부 batch와 DELETE 전후에 lease를 연장하며 소유권 상실·인터럽트·Qdrant 오류를 기존 worker 실패 처리로 전달한다.
- 조건부 `RagIndexJobScheduler`는 한 번에 설정된 최대 작업 수까지 소비하고 빈 큐, 처리 실패, lease 상실과 기반 오류를 구분한다. 기본 lease는 외부 호출 시간을 고려해 300초다.
- Docker Compose에 Qdrant 1.19.1과 영속 volume, HTTP 6333·gRPC 6334 포트를 추가했다.
- 설정·chunk·Processor·scheduler 20개와 기존 MySQL RAG 회귀를 포함한 RAG 236개, 프로젝트 전체 548개가 성공했다. 실패·오류·건너뜀은 0이며 실제 OpenAI·Qdrant
  네트워크 호출은 포함하지 않았다. 검색 서비스는 2026-09-09 구현됐고, 2026-09-11 실제 OpenAI·Qdrant UPSERT·검색·DELETE smoke test까지 완료했다.

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
- 활성 generation 조회를 제공해 외부 검색 후보가 현재 검색 가능한 generation인지 확인한다. Processor·scheduler·Qdrant 색인과 검색 연결까지 완료했으며 실제 네트워크
  smoke test는 남아 있다.
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

### 회원 탈퇴 개인 문서 RAG 정리 (2026-09-11)

- 회원 탈퇴는 회원 행을 먼저 비관적으로 잠근 뒤 자기소개서와 이력서를 ID 순서로 비관적 잠금 조회한다.
- 잠근 모든 자기소개서·이력서에 RAG DELETE 작업을 같은 쓰기 트랜잭션으로 등록한 후 회원을 삭제해 DB cascade와 tombstone 무효화를 원자적으로 처리한다.
- 이력서 원본 파일은 모든 RAG DELETE 등록이 끝난 뒤 커밋 후 삭제로 예약한다. RAG 등록 실패 시 회원·개인 문서와 파일을 유지하고 등록 작업도 롤백한다.
- RAG 변경을 flush한 뒤 `deleteAllByIdInBatch`로 사용자 ID를 삭제해 DB cascade를 실행한다. 문서를 로드한 영속성 컨텍스트에서 사용자 엔티티를 직접 삭제할 때 발생하는 참조
  오류를 방지하며 rollback 시 문서·RAG 순번·파일을 모두 유지한다.
- `UserServiceTest` 18개와 프로젝트 전체 564개가 성공했으며 실패·오류·건너뜀은 0이다.

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
    - 회원·자기소개서·이력서를 잠그고 개인 문서별 RAG DELETE를 등록한 뒤 cascade 삭제
    - 모든 RAG 등록 성공 후에만 이력서 파일을 트랜잭션 커밋 이후 정리
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
