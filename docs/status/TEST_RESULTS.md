# 테스트 실행 기록

[프로젝트 현황으로 돌아가기](../PROJECT_STATUS.md)

기존 현황 문서에 기록된 사용자 실행 결과를 보존한다. 최신 전체 검증은 2026-09-08의 528개 성공이며, 이전 기록의 검증 대기·실패·건너뜀 표시는 당시 상태다.

### RAG 활성 generation·DELETE tombstone 검증 (2026-09-08)

- DB 실패 롤백 단건 재실행:
  `.\gradlew.bat test --tests "com.interviewai.rag.service.RagActiveGenerationIntegrationTest.sourceUpdateFailureAlsoRollsBackJdbcJobSuccess"` —
  BUILD SUCCESSFUL (26초).
- 사용자 선택 실행: `.\gradlew.bat test --tests "com.interviewai.rag.*"` — BUILD SUCCESSFUL (1분 29초).
- 사용자 전체 실행: `.\gradlew.bat test` — BUILD SUCCESSFUL (3분 15초). 최신 XML 58개에서 전체 528개 성공, 실패 0, 오류 0, 건너뜀 0을 확인했다.
- 전체 결과의 RAG 테스트는 17개 클래스 216개다. 신규 `RagActiveGenerationIntegrationTest` 25개,
  `RagActiveGenerationMigrationIntegrationTest` 1개, `RagClaimedJobGenerationTest` 2개와 기존 테스트 추가 9개로 합계 37개가 포함됐다.
- 실제 MySQL에서 V9 이관과 CHECK, DELETE 등록 즉시 무효화·롤백, 최신 generation 교체, 오래된 완료·만료·재선점 attempt 차단, 작업 성공과 활성화의 원자성 및 등록/완료 동시성을
  검증했다.
- 최초 DB 실패 롤백 테스트는 binary logging 환경에서 trigger 생성에 `SUPER` 권한이 없어 RAG 216개 중 1개가 실패했다. 같은 실패를 임시 unique index로 유도하도록 수정한
  뒤 단건·RAG 전체·전체 회귀가 모두 성공했다.
- Codex는 테스트를 직접 실행하지 않았으며 사용자 실행 결과와 최신 XML을 대조했다. OpenJDK class-data sharing 경고는 테스트 성공에 영향을 주지 않았다.

### RAG DB 작업 실행 기반 검증 (2026-09-08)

- 사용자 선택 실행: `.\gradlew.bat test --tests "com.interviewai.rag.*"` — BUILD SUCCESSFUL (1분 11초), 사용자 출력 확인.
- 사용자 전체 실행: `.\gradlew.bat test` — BUILD SUCCESSFUL (2분 43초). XML 55개에서 전체 491개 성공, 실패 0, 오류 0, 건너뜀 0을 확인했다.
- 전체 결과의 RAG 테스트는 14개 클래스 179개다. 신규 `RagIndexJobExecutionServiceTest` 22개, `RagIndexJobWorkerTest` 10개,
  `RagIndexJobExecutionServiceIntegrationTest` 19개로 합계 51개가 포함됐다.
- 실제 MySQL 통합 테스트가 건너뜀 없이 실행되어 V8 CHECK, DB 선점·SKIP LOCKED, 동시 worker 중복 방지, lease 연장·만료 재선점, 이전 attempt 차단, 재시도 지연·소진,
  롤백과 독립 커밋을 확인했다.
- worker의 정상 완료, 처리 예외·인터럽트, lease 상실, DB 오류 전파와 실패 코드 정규식 정상·경계·실패 입력을 확인했다.
- Codex는 테스트를 직접 실행하지 않았으며 사용자 실행 결과와 XML을 대조했다. OpenJDK class-data sharing 경고는 테스트 성공에 영향을 주지 않았다.

### 자기소개서·이력서 RAG 등록 연결 검증 (2026-09-08)

- 사용자 선택 실행:
  `./gradlew test --tests "com.interviewai.coverletter.service.CoverLetterServiceTest" --tests "com.interviewai.resume.service.ResumeServiceTest" --tests "com.interviewai.rag.service.RagSourceChangeRegistrationServiceTest"` —
  BUILD SUCCESSFUL (3초). XML에서 자기소개서 서비스 14개, 이력서 서비스 11개, 공용 등록 서비스 7개로 합계 32개 성공, 실패·오류·건너뜀 0을 확인했다.
- 최초 선택 실행은 자기소개서 생성 테스트의 중첩 Mockito `verify` 때문에 32개 중 1개가 실패했다. 검증 대상 인자를 각각 먼저 캡처하도록 테스트를 수정했고 위 재실행에서 성공했다.
- 사용자 전체 실행: `./gradlew test --rerun-tasks` — BUILD SUCCESSFUL (1분 15초). 최신 XML 52개에서 전체 440개 성공, 실패 0, 오류 0, 건너뜀 0을
  확인했다.
- 자기소개서 생성·수정·복원 UPSERT와 삭제 DELETE, 이력서 생성·제목 수정·파일 교체의 추출 상태별 등록과 삭제 DELETE를 검증했다. 등록 실패 시 삭제를 진행하지 않는 실패 경로와 이력서 파일 정리
  예약 순서도 포함한다.
- Codex는 테스트를 직접 실행하지 않았으며 사용자 실행 결과와 XML을 대조했다. OpenJDK class-data sharing 경고는 테스트 성공에 영향을 주지 않았다.

### 기업·채용공고 RAG 등록 연결 검증 (2026-09-08)

- 최종 사용자 선택 재실행: Colima 소켓을 명시한 `CatalogConcurrencyIntegrationTest` — BUILD SUCCESSFUL (16초). XML에서 2개 성공, 실패·오류·건너뜀 0을
  확인했다.
- 최종 사용자 전체 재실행: Colima 소켓을 명시한 `./gradlew test --rerun-tasks` — BUILD SUCCESSFUL (1분 16초). 최신 XML 52개에서 전체 435개 성공, 실패
  0, 오류 0, 건너뜀 0을 확인했다.
- `existsByCompanyId`에 비관적 읽기 잠금을 적용한 뒤 기업 삭제와 공고 생성 경합이 `COMPANY_NOT_FOUND` 또는 `COMPANY_HAS_JOB_POSTINGS` 정책으로 직렬화되는 것을
  실제 MySQL에서 검증했다.
- 아래 실패 및 건너뜀 결과는 최종 수정 전 진단 기록이다.
- Colima의 `DOCKER_HOST`와 `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`를 지정한 사용자 전체 재실행: `./gradlew test --rerun-tasks` — BUILD
  FAILED (1분 23초).
- 최신 XML에서 전체 435개가 건너뜀 없이 실행되어 434개 성공, 실패 1개, 오류 0, 건너뜀 0을 확인했다.
- 실패 테스트는 `CatalogConcurrencyIntegrationTest.serializesCompanyDeletionAndPostingCreation`이다. 기업 삭제 트랜잭션이 동시 생성된 공고를
  `existsByCompanyId`의 일관 읽기로 보지 못한 뒤 커밋 시 `fk_job_postings_company` 위반으로 실패했다.
- 수정 후 해당 동시성 테스트와 전체 회귀 테스트를 다시 실행해야 한다.
- 아래 BUILD SUCCESSFUL 기록은 Docker가 탐지되지 않아 통합 테스트 64개가 건너뛰어진 이전 실행이다.
- 사용자 선택 실행:
  `./gradlew test --tests "com.interviewai.rag.service.RagSourceChangeRegistrationServiceTest" --tests "com.interviewai.catalog.CatalogApiTest" --tests "com.interviewai.catalog.CatalogConcurrencyIntegrationTest"` —
  BUILD SUCCESSFUL (16초).
- 사용자 전체 실행: `./gradlew test` — BUILD SUCCESSFUL (9초).
- 전체 실행 후 최신 XML 52개에서 403개 발견, 339개 성공, 실패 0, 오류 0, 건너뜀 64를 확인했다.
- `RagSourceChangeRegistrationServiceTest` 7개와 `CatalogApiTest` 61개는 성공했다. `CatalogConcurrencyIntegrationTest` 2개를 비롯해
  MySQL/Testcontainers 통합 테스트 64개는 건너뛰어졌다.
- 따라서 공용 등록 정책과 MVC 연결은 검증 완료이며, 실제 MySQL에서 원본 변경과 작업 등록의 원자성 및 기존 동시성 회귀는 검증 대기 상태다.

### RAG 작업 등록 영속화 검증 (2026-09-07)

- JPA가 관리하는 `lockVersion`의 초기값과 IDE 경고 억제를 명시한 뒤 사용자 재실행:
  `.\gradlew.bat test --tests "com.interviewai.rag.service.RagIndexJobRegistrationServiceIntegrationTest"` — BUILD
  SUCCESSFUL (28초). 최신 XML에서 40개 성공, 실패·오류·건너뜀 0 확인.
- SQL 문자열 조합 경고를 고정 SQL과 값 바인딩으로 수정한 뒤 사용자 실행:
  `.\gradlew.bat test --tests "com.interviewai.rag.service.RagIndexJobRegistrationServiceIntegrationTest"` — BUILD
  SUCCESSFUL (29초). 최신 XML에서 40개 성공, 실패·오류·건너뜀 0 확인.
- 사용자 선택 실행: `.\gradlew.bat test --tests "com.interviewai.rag.*"` — BUILD SUCCESSFUL (41초). 선택 실행 성공은 사용자 출력으로 확인했으며
  XML은 후속 전체 실행으로 갱신되었다.
- 사용자 전체 실행: `.\gradlew.bat test` — BUILD SUCCESSFUL (2분 19초). 최신 XML 51개에서 전체 428개 성공, 실패·오류·건너뜀 0 확인.
- RAG 10개 클래스 121개 성공. 신규 `RagIndexJobEntityTest` 6개·`RagIndexJobRegistrationServiceIntegrationTest` 40개를 포함하며 MySQL 통합
  테스트도 건너뜀 없이 실행되었다.
- 실제 V7·작업 엔티티·Repository·등록 서비스를 확인했다. 네 원본 유형의 UPSERT 스냅샷 저장·복원, 원본 없는 DELETE 등록, 초기 PENDING·UTC 마이크로초 시각, 동일 트랜잭션 연속
  등록·독립 순번, 호출자 트랜잭션 요구, 롤백·동시 등록·DB 제약·overflow를 검증했다.
- 실제 작업 INSERT의 unique 충돌 시 순번·버전이 유지되고, 호출자 실패 또는 rollback-only 시 작업과 순번이 함께 롤백되는 것을 검증했다.
- 완료 범위는 작업 본문·초기 상태와 순번의 원자적 등록이다. 기존 CRUD 연결, 원본 변경과 등록의 원자성·스냅샷 조회 시점 정책, 실행 상태 전이 저장, worker·lease·활성
  generation·tombstone·외부 색인 연결은 후속 범위다.
- Codex는 테스트를 실행하지 않고 사용자 출력과 최신 XML을 확인했다. OpenJDK class-data sharing 경고가 출력되었지만 두 실행 모두 성공했다.

### RAG 원본별 순번 영속화·등록 직렬화 기반 검증 (2026-09-07)

- 경고 수정 후 사용자 실행: `.\gradlew.bat test --tests "com.interviewai.rag.service.RagIndexSequenceServiceIntegrationTest"` —
  BUILD SUCCESSFUL (28초). 최신 XML에서 15개 성공, 실패·오류·건너뜀 0 확인. 전체 실행은 다시 수행하지 않았다.
- DELETE는 테스트 원본 키만, UPDATE는 대상 원본만 처리하도록 제한하고 UPDATE 영향 행 1개를 검사한다. Executor는 try-with-resources 및 shutdownNow를 사용하고
  awaitTermination 호출을 제거했다. close는 실제 종료까지 기다리므로 기존 종료 대기 10초 제한과 동작이 다르다.
- 사용자 커밋 `84f81cb`에 구현·테스트와 위 경고 수정이 포함된 것을 확인했다. 확인 시 작업 트리는 깨끗했다. 아래 전체 382개 기록은 경고 수정 이전 실행이다.

- 사용자 선택 실행: `.\gradlew.bat test --tests "com.interviewai.rag.*"` — BUILD SUCCESSFUL (34초). 성공은 사용자 출력으로 확인했으며 선택 실행
  XML은 후속 전체 실행으로 갱신되었다.
- 사용자 전체 실행: `.\gradlew.bat test` — BUILD SUCCESSFUL (1분 55초). 최신 XML 49개에서 전체 382개 성공, 실패 0, 오류 0, 건너뜀 0을 확인했다.
- RAG 8개 클래스 75개 성공. 신규 `RagIndexSourceTest` 2개와 `RagIndexSequenceServiceIntegrationTest` 15개를 포함하며 MySQL 통합 테스트도 건너뜀 없이
  실행되었다.
- 실제 V6·엔티티·Repository·서비스를 확인했다. 원본 키 unique, 최초 생성 경합 처리, 행 잠금, 호출자 트랜잭션 참여, 순번·버전 저장과 롤백, overflow 및 DB CHECK 제약을
  검증했다.
- 완료 범위는 원본별 순번 영속화와 등록 직렬화 기반이다. 작업 본문·상태 저장 및 기존 API 연결, worker 선점·lease, 활성 generation·삭제 tombstone과 외부 색인·검색은 미구현이다.
- Codex는 테스트를 직접 실행하지 않았다. OpenJDK class-data sharing 경고가 출력되었으나 두 사용자 실행 모두 성공했고 전체 XML에도 실패·오류가 없다.

### 기업·채용공고 최신 검증 (2026-09-07)

- DTO·Service·Controller 4개와 전역 예외 연결 반영을 확인했다. 생성 Location의 경로 구분자와 관심 해제의 Service 호출 수정도 확인했다.
- `CatalogApiTest`, `CatalogInputTest`, `CatalogRepositoryIntegrationTest`, `CatalogConcurrencyIntegrationTest`를 작성했다.
  실제 Service·DB 역할 검사, 입력 경계, MySQL 제약·검색·페이징·상태·cascade와 동시성 시나리오를 포함한다.
- 2026-09-07 사용자 실행:
  `.\gradlew.bat test --tests "com.interviewai.catalog.*" --tests "com.interviewai.global.validation.CatalogInputTest" --console=plain --rerun-tasks` —
  102개 중 2개 실패. CHECK 제약은 정상 동작했으나 테스트가 Spring 예외 타입을 잘못 예상했다.
- 두 테스트를 SQL 오류 코드 `3819`, SQLState `HY000`과 해당 CHECK 제약 이름을 검사하도록 수정했다.
- 2026-09-07 사용자 재실행:
  `.\gradlew.bat test --tests "com.interviewai.catalog.CatalogRepositoryIntegrationTest" --console=plain` — BUILD
  SUCCESSFUL. 실제 XML에서 17개, 실패 0, 오류 0, 건너뜀 0을 확인했다.
- 2026-09-07 사용자 전체 실행: `.\gradlew.bat test --console=plain` — BUILD SUCCESSFUL. 실제 XML 41개에서 전체 307개, 실패 0, 오류 0, 건너뜀
  0을 확인했다. 신규 기업·채용공고 및 입력 검증 102개를 포함하며 MySQL 통합 테스트도 실행되었다.
- 현재 상태: 기업·채용공고 API와 RAG 문서·변환기·접근 제어·metadata·메모리 내 색인 작업 모델 구현 및 전체 회귀 검증 완료. 최신 전체 365개 성공을 확인했으며, 다음 작업은 색인 작업 영속화와
  동시 실행 제어 설계·구현이다.
- 2026-09-07 경고 정리 후 사용자 실행: `.\gradlew.bat test --tests "com.interviewai.catalog.CatalogConcurrencyIntegrationTest"` —
  BUILD SUCCESSFUL. 실제 XML에서 2개 성공, 실패·오류·건너뜀 0을 확인했다. count 반환형을 Integer로 변경하고 Executor에 try-with-resources를 적용했으며
  finally의 throw를 제거했다. 자원 정리는 작업 종료를 기다리므로 기존 종료 대기 30초 제한과는 동작이 다르다.
- 같은 작업 트리의 `GlobalExceptionHandler.handleMessageNotReadable()` 미사용 매개변수 제거도 확인했다. 이번 실행은 동시성 테스트만 포함하므로 해당 MVC 변경의 재검증은
  대기 상태다. 이전 전체 307개 성공 기록은 경고 정리 이전 결과다.
- 실행 환경: 사용자 터미널에서 JDK 21 선택 및 UTF-8 출력 설정 후 테스트를 진행했다. 한글 테스트 이름은 최신 사용자 출력에서 정상 표시된다.

### RAG 문서 모델 최신 검증 (2026-09-07)

- `RagVisibility`, `RagSourceType`, `RagSourceKey`, `RagSourceSnapshot`을 추가했다.
- 기업·채용공고는 인증 사용자 공용 문서, 자기소개서·이력서는 사용자 소유 개인 문서로 구분한다.
- 양수 원본 ID, 개인 문서 소유자, 기업·채용공고의 기업 ID와 제목·본문·revision의 비어 있지 않은 값을 생성 시점에 검증한다.
- 사용자가 실행한 `.\gradlew.bat test --tests "com.interviewai.rag.document.RagSourceDocumentTest" --console=plain`에서 BUILD
  SUCCESSFUL을 확인했다.
- 실제 XML에서 14개 성공, 실패 0, 오류 0, 건너뜀 0을 확인했다.
- 예외 발생만 검증하는 테스트 helper 3개의 사용되지 않는 반환 값 경고를 제거한 뒤 사용자가 같은 선택 테스트를 재실행했다. BUILD SUCCESSFUL과 실제 XML 14개 성공·실패 0·오류 0·건너뜀
  0을 다시 확인했다.
- `RagSourceStatus`, `RagSourceResolution`, `RagSourceSnapshotFactory`, `RagSourceAccessService`를 추가했다. 기업·채용공고의 공용 변환,
  현재 자기소개서 버전과 사용자 소유권, 이력서 추출 상태별 색인 가능 여부 및 실제 색인 내용 기반 SHA-256 revision을 적용한다.
- 2026-09-07 사용자 실행: `.\gradlew.bat test --tests "com.interviewai.rag.*"` — BUILD SUCCESSFUL. 실제 XML 3개에서 전체 33개, 실패 0,
  오류 0, 건너뜀 0을 확인했다. 신규 변환기 테스트 10개와 접근 제어 서비스 테스트 9개를 포함한다.
- `RagSourceSnapshotFactoryTest`에서 항상 1이 전달되던 현재 버전 helper 매개변수 2개를 제거한 뒤 사용자가 같은 RAG 선택 테스트를 재실행했다. BUILD SUCCESSFUL과
  실제 XML 전체 33개 성공·실패 0·오류 0·건너뜀 0을 다시 확인했다.
- 실행 중 출력된 OpenJDK class-data sharing 경고는 Mockito의 테스트용 클래스 계측 과정에서 발생한 JVM 경고이며 테스트 실패나 애플리케이션 실행 결과가 아니다.
- 위 33개 실행 당시에는 RAG 선택 테스트만 포함해 전체 회귀 검증이 대기 상태였다. 이후 아래 전체 365개 실행으로 RAG 추가 코드와 기존 MVC 변경을 포함한 회귀 검증을 완료했다.

### Qdrant metadata와 색인 작업 모델 최신 검증 (2026-09-07)

- `RagIndexTarget`, `RagIndexOperation`, `RagIndexJobStatus`, `RagIndexStatus`, `RagIndexJob`, `RagChunkMetadata`를 추가했다.
- metadata는 검증된 스냅샷에서 생성하며 문서 유형·공개 범위·원본 revision·색인 설정 버전·generation·chunk 정보와 해당 유형의 소유자·기업·공고 ID를 포함한다. metadata
  Map은 불변이며 원본 본문은 포함하지 않는다.
- `pipelineVersion`으로 전처리·chunk·embedding 설정 변경을 구분하고 작업 UUID를 generation으로 사용한다. 동일 작업·chunk는 재시도 시 같은 point UUID를 유지하며
  새 작업은 다른 generation과 point ID를 사용한다.
- 작업 상태는 `PENDING → RUNNING → SUCCEEDED/FAILED`, 실패 후 제한 횟수 내 재시도, 대기·실행·실패 작업 취소를 지원한다. 실행별 attempt UUID로 이전 실행의 늦은
  완료·실패 응답을 거부한다.
- 삭제 작업은 원본 스냅샷 없이 원본 키로 생성한다. 색인 상태는 해당 작업의 목표 상태이며 현재 검색 가능한 generation의 상태를 의미하지 않는다.
- `RagChunkMetadata.from()`의 범위 검사를 `chunkIndex < 0 || chunkIndex >= chunkCount`로 수정했다. 음수 chunk 번호와 0 이하 chunk 개수를
  거부하면서 중복 조건 IDE 경고를 제거했다.
- 정상·경계·실패 테스트 25개를 추가했다: `RagIndexTargetTest` 3개, `RagIndexJobTest` 11개, `RagChunkMetadataTest` 11개.
- 2026-09-07 사용자 선택 실행: `.\gradlew.bat test --tests "com.interviewai.rag.*"` — BUILD SUCCESSFUL. 선택 실행 성공은 사용자 출력으로
  확인했다. 이후 전체 실행으로 XML이 갱신되어 선택 실행 당시의 XML은 별도로 남아 있지 않다.
- 2026-09-07 사용자 전체 실행: `.\gradlew.bat test` — BUILD SUCCESSFUL. 실제 최신 XML에서 전체 365개 성공, 실패 0, 오류 0, 건너뜀 0을 확인했다. RAG 6개
  클래스 58개 성공이며 이번 추가 25개를 포함한다.
- 현재 범위는 메모리 내 모델이다. DB 영속화, worker 선점·lease·동시 실행 제어, 활성 generation 교체, 삭제 tombstone, 외부 요청 중단·부분 기록 정리, Spring
  AI·Qdrant 연결 및 실제 색인·검색은 미구현이다.

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

2026-09-03 사용자 Windows 로컬 환경에서 자기소개서 관련 테스트와 전체 테스트를 실행했다.

- `.\gradlew.bat test --tests "com.interviewai.coverletter.*"`: 성공 (`BUILD SUCCESSFUL in 34s`,
  `4 actionable tasks: 2 executed, 2 up-to-date`)
- `.\gradlew.bat test`: 성공 (`BUILD SUCCESSFUL in 1m 9s`, `4 actionable tasks: 1 executed, 3 up-to-date`)
- 전체 테스트 리포트: 178개 실행, 실패 0개, 오류 0개, 건너뜀 0개
- CRUD, 사용자별 소유권, validation 경계, immutable 버전 추가·조회·복원, 대표 설정·교체·해제, Flyway V3 및 MySQL unique·복합 외래 키·cascade를 검증함

2026-09-03 사용자 Windows 로컬 환경에서 이력서 관련 테스트와 전체 테스트를 실행했다.

- `.\gradlew.bat test --tests "com.interviewai.resume.*"`: 성공 (`BUILD SUCCESSFUL in 36s`,
  `4 actionable tasks: 2 executed, 2 up-to-date`)
- `.\gradlew.bat test`: 성공 (`BUILD SUCCESSFUL in 1m 3s`, `4 actionable tasks: 1 executed, 3 up-to-date`)
- 전체 테스트 리포트: 205개 실행, 실패 0개, 오류 0개, 건너뜀 0개
- multipart API, PDF 검증·텍스트 추출·SHA-256, 크기 제한, 소유권, 다운로드, 파일 교체·정리, 대표 설정, 경로 이탈 방지, Flyway V4 복합 외래 키·cascade를 검증함
