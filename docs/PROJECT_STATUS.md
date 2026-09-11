# Interview AI Backend 프로젝트 현황

최종 갱신일: 2026-09-11

개발을 재개할 때 먼저 읽는 기준 문서다. 현재 상태와 다음 작업을 요약하고, 상세 내용과 과거 기록은 아래 문서에서 관리한다.

## 현재 상태

| 영역                   | 상태                                                                          |
|----------------------|-----------------------------------------------------------------------------|
| 인증·회원 관리             | 구현·검증 완료: JWT, Refresh Token, Google·GitHub 로그인, 회원정보·비밀번호 변경, 탈퇴           |
| 자기소개서                | 구현·검증 완료: CRUD, 버전 이력·복원, 대표 설정                                             |
| 이력서                  | 구현·검증 완료: PDF 저장·검증·추출, CRUD·다운로드, 대표 설정                                    |
| 기업·채용공고              | 구현·검증 완료: 관리자 관리, 사용자 조회·검색, 관심 기업                                          |
| RAG 기반 모델            | 구현·검증 완료: 원본 스냅샷·변환·접근 제어, metadata, 메모리 내 색인·삭제 작업 수명주기                    |
| RAG 순번·등록 직렬화 기반     | 구현·검증 완료: V6·JPA 순번 저장·최초 생성 경합·행 잠금·롤백                                     |
| RAG 작업 등록 영속화        | 구현·검증 완료: V7·UPSERT 스냅샷·DELETE 키·초기 PENDING 저장, 순번과 등록의 동시 커밋·롤백            |
| RAG CRUD 연결          | 기업·채용공고·자기소개서·이력서의 UPSERT·DELETE 등록 연결 및 전체 회귀 검증 완료                        |
| RAG 작업 실행 기반         | 구현·검증 완료: V8·DB 선점·attempt·lease·재시도·worker·늦은 상태 변경 차단                     |
| RAG generation·삭제 차단 | 구현·검증 완료: V9·활성 generation 교체·DELETE tombstone·오래된 외부 쓰기 비활성화               |
| RAG 외부 색인            | 구현·전체 회귀 검증 완료: 700/100 token chunk·Processor·scheduler·Spring AI·Qdrant 연결 |
| RAG 외부 검색            | 구현·전체 회귀 검증 완료: 인증 범위 metadata·활성 generation·원본 존재/소유권·Top-K 5              |
| RAG 실제 외부 연동         | 검증 완료: 실제 OpenAI embedding·Qdrant 네트워크 UPSERT·개인 검색·DELETE smoke test       |
| 회원 탈퇴 RAG 정리         | 구현·검증 완료: 개인 문서 잠금·DELETE 등록·cascade 삭제·커밋 후 이력서 파일 정리                      |
| 운영 실행 기반             | profile·컨테이너·health·OAuth2 proxy·로그·정리 scheduler 정책 검증 완료                   |
| 면접·평가·성장 분석 및 실제 배포  | 후속 범위                                                                       |

## 다음 작업

1. AI 질문 생성과 면접 세션의 API·데이터 모델·RAG context 연결 정책을 구체화한다.

자기소개서 PDF 업로드는 이력서의 파일 저장·검증·추출 기반을 공통화하는 별도 후속 작업이다. 실제 배포 환경 선정은 MVP 핵심 흐름 완성 이후 진행한다.

## 최신 검증

- 실행일: 2026-09-11
- 회원 탈퇴 시 회원·자기소개서·이력서를 순서대로 비관적 잠금하고, 개인 문서별 RAG DELETE를 모두 등록한 뒤 DB cascade 삭제와 이력서 파일의 커밋 후 정리를 수행하도록 보강했다.
- 사용자 선택 실행: `UserServiceTest` — 최종 BUILD SUCCESSFUL, 18개 성공, 실패·오류·건너뜀 0.
- 사용자 전체 실행: `.\gradlew.bat test` — BUILD SUCCESSFUL (3분 23초). XML 64개에서 전체 564개 성공, 실패·오류·건너뜀 0을 확인했다.
- OpenJDK class-data sharing 경고는 결과에 영향을 주지 않았다.
- 실행일: 2026-09-11
- 실제 OpenAI embedding·로컬 Qdrant smoke test에서 UPSERT, 개인 자기소개서 검색, DELETE point 제거, 삭제 원본 미노출이 모두 성공했다. smoke 전용 사용자·DB
  원본·Qdrant point는 정리했다.
- 최초 smoke test에서 Spring AI가 `Long` metadata를 문자열로 저장해 숫자 필터와 불일치하는 문제를 발견했다. 식별자 metadata·필터를 문자열로 통일하고 DELETE 순번 범위용
  숫자 `sourceSequenceOrder`를 분리했다.
- 사용자 단위 실행: `QdrantRagIndexProcessorTest`, `RagSearchServiceTest` — BUILD SUCCESSFUL (10초), 14개 성공.
- 사용자 RAG 전체 실행: `.\gradlew.bat test --tests "com.interviewai.rag.*"` — BUILD SUCCESSFUL (1분 11초). XML 23개에서 250개 성공,
  실패·오류·건너뜀 0을 확인했다.
- 사용자 전체 실행: `.\gradlew.bat test` — BUILD SUCCESSFUL (3분 18초). XML 64개에서 전체 562개 성공, 실패·오류·건너뜀 0을 확인했다.
- OpenJDK class-data sharing 경고는 결과에 영향을 주지 않았다.
- 실행일: 2026-09-09
- 환경변수를 제거한 단건 프로필 재실행 — BUILD SUCCESSFUL (8초), 4개 성공.
- 최신 사용자 전체 실행: `./gradlew test` — BUILD SUCCESSFUL (1분 38초). XML 64개에서 전체 562개 성공, 실패·오류·건너뜀 0을 확인했다.
- 최초 전체 실행과 프로필 단건 재실패는 `.zshrc`의 `SPRING_PROFILES_ACTIVE=local`, `REFRESH_TOKEN_CLEANUP_ENABLED=true`가 prod 기본값을 덮어쓴 실행
  환경 문제였다. 두 환경변수를 제거하고 Gradle Daemon을 재시작해 해결했다.
- 검색 서비스·접근 제어·컨트롤러 선택 실행 — BUILD SUCCESSFUL (16초).
- RAG 전체 선택 실행: `./gradlew test --tests "com.interviewai.rag.*"` — BUILD SUCCESSFUL (47초). XML 23개에서 250개 성공, 실패·오류·건너뜀
  0을 확인했다.
- 검색은 Qdrant metadata 범위, 활성 generation, 현재 원본 존재·소유권, 최대 5개 반환과 프로젝트 전체 회귀를 검증했다. 실제 OpenAI·Qdrant 네트워크 호출은 아직 수행하지
  않았다.
- 실행일: 2026-09-08
- 신규 설정·chunker·Processor·scheduler 20개 선택 실행 — BUILD SUCCESSFUL (14초), 실패·오류·건너뜀 0.
- lease 고정값 충돌을 수정한 단건 재실행 — BUILD SUCCESSFUL (27초).
- 최신 RAG 선택 실행: `.\gradlew.bat test --tests "com.interviewai.rag.*"` — BUILD SUCCESSFUL (1분 28초).
- 최신 사용자 전체 실행: `.\gradlew.bat test` — BUILD SUCCESSFUL (3분 26초). XML 62개에서 전체 548개 성공, 실패·오류·건너뜀 0을 확인했다.
- RAG XML 21개에서 236개 성공했다. 실제 OpenAI·Qdrant 네트워크 호출은 아직 검증하지 않았다.
- 실행일: 2026-09-08
- 최신 사용자 선택 실행: `.\gradlew.bat test --tests "com.interviewai.rag.*"` — BUILD SUCCESSFUL (1분 29초).
- 최신 사용자 전체 실행: `.\gradlew.bat test` — BUILD SUCCESSFUL (3분 15초). XML 58개에서 전체 528개 성공, 실패·오류·건너뜀 0 확인.
- RAG 17개 클래스 216개가 성공했다. 신규 37개에서 V9 이관, 활성 generation 교체, DELETE 즉시 무효화, 늦은 완료·재선점 차단, 롤백·동시성을 검증했다.
- DB 실패 롤백 테스트의 trigger 권한 문제는 임시 unique index 방식으로 수정했고 단건 재실행도 26초에 성공했다. OpenJDK class-data sharing 경고는 결과에 영향을 주지
  않았다.
- 아래 기록은 이전 작업 실행 기반 검증 이력이다.
- 사용자 선택 실행: `.\gradlew.bat test --tests "com.interviewai.rag.*"` — BUILD SUCCESSFUL (1분 11초).
- 사용자 전체 실행: `.\gradlew.bat test` — BUILD SUCCESSFUL (2분 43초). XML 55개에서 전체 491개 성공, 실패·오류·건너뜀 0 확인.
- 아래 기록은 이전 CRUD 연결 검증 이력이다.
- 최신 사용자 전체 실행: `./gradlew test --rerun-tasks` — BUILD SUCCESSFUL (1분 15초). XML 52개에서 전체 440개 성공, 실패·오류·건너뜀 0 확인.
- 자기소개서·이력서 선택 실행은 서비스 테스트와 공용 변경 등록 서비스 테스트 합계 32개가 성공했고 실패·오류·건너뜀은 0이다.
- 자기소개서 생성·수정·복원과 이력서 생성·제목 수정·파일 교체는 UPSERT 또는 추출 상태 기반 DELETE를 등록하며, 원본 삭제는 DELETE 작업을 먼저 등록한다.
- 아래 435개 전체 실행 기록은 기업·채용공고 연결 완료 당시의 직전 검증이다.
- 직전 사용자 전체 실행: Colima 소켓을 명시한 `./gradlew test --rerun-tasks` — BUILD SUCCESSFUL (1분 16초). XML 52개에서 전체 435개 성공,
  실패·오류·건너뜀 0 확인.
- 사용자 선택 재실행: `CatalogConcurrencyIntegrationTest` — BUILD SUCCESSFUL (16초), XML에서 2개 성공, 실패·오류·건너뜀 0 확인.
- 공고 존재 확인을 비관적 읽기 잠금으로 변경해 기업 삭제와 공고 생성 경합이 도메인 정책대로 직렬화되는 것을 실제 MySQL에서 확인했다.
- 아래 실패·건너뜀 기록은 수정 전 진단 이력이다.
- 사용자 선택 실행:
  `./gradlew test --tests "com.interviewai.rag.service.RagSourceChangeRegistrationServiceTest" --tests "com.interviewai.catalog.CatalogApiTest" --tests "com.interviewai.catalog.CatalogConcurrencyIntegrationTest"` —
  BUILD SUCCESSFUL (16초).
- Colima 소켓을 명시한 사용자 전체 재실행: `./gradlew test --rerun-tasks` — BUILD FAILED (1분 23초). XML에서 435개 실행, 성공 434개, 실패 1개,
  오류·건너뜀 0을 확인했다.
- 실패는 `CatalogConcurrencyIntegrationTest.serializesCompanyDeletionAndPostingCreation`에서 기업 삭제가 동시 커밋된 공고를 일반 `exists`
  조회로 보지 못하고 커밋 시 `fk_job_postings_company` 제약에 걸린 경우다. 잠금 읽기 수정과 재검증이 필요하다.
- 앞선 Docker 미탐지 실행은 BUILD SUCCESSFUL이었지만 MySQL/Testcontainers 통합 테스트 64개가 건너뛰어졌으므로 완료 근거로 사용하지 않는다.
- 아래 2026-09-07 기록은 직전 건너뜀 없는 전체 검증 기준이다.
- 최신 사용자 실행: `.\gradlew.bat test --tests "com.interviewai.rag.service.RagIndexJobRegistrationServiceIntegrationTest"` —
  BUILD SUCCESSFUL (28초). SQL 문자열 조합과 JPA `lockVersion` IDE 경고 정리 후 XML에서 40개 성공, 실패·오류·건너뜀 0 확인.
- 사용자 선택 실행: `.\gradlew.bat test --tests "com.interviewai.rag.*"` — BUILD SUCCESSFUL (41초), 사용자 출력 확인.
- 최신 전체 실행: `.\gradlew.bat test` — BUILD SUCCESSFUL (2분 19초). XML 51개에서 전체 428개 성공, 실패·오류·건너뜀 0 확인. RAG 10개 클래스 121개이며
  신규 단위 6개·MySQL 통합 40개를 포함한다.
- Codex는 테스트를 실행하지 않고 사용자 출력과 XML을 확인했다. 상세 근거와 이전 결과는 [테스트 실행 기록](status/TEST_RESULTS.md)을 참고한다.

## 상세 문서

| 문서                                   | 확인할 내용                             |
|--------------------------------------|------------------------------------|
| [구현 상세](status/IMPLEMENTATION.md)    | 실행 환경, 인증·사용자·자기소개서·이력서 API, DB 구조 |
| [기업·채용공고](status/CATALOG.md)         | API, 도메인 정책과 설계 결정, 단계별 과거 기록      |
| [자동 테스트 목록](status/TEST_COVERAGE.md) | 테스트별 검증 범위                         |
| [테스트 실행 기록](status/TEST_RESULTS.md)  | 실행 날짜·명령·성공·실패와 RAG 최신 검증 범위       |
| [개발 로드맵](status/ROADMAP.md)          | 전체 구현 순서와 후속 기능                    |
| [주요 결정과 확인 사항](status/DECISIONS.md)  | API·인증·운영 정책과 남은 확인 사항             |
| [변경 이력](status/HISTORY.md)           | 과거 변경사항과 분리 전 재개 메모                |

## 실행 환경과 Git 기준점

- Java 21, Gradle Wrapper 9.5.1, Spring Boot 4.1.0, MySQL 8.4 및 Flyway
- 기본 profile: `local`, 기본 서버 포트: `8080`
- 브랜치: `main`
- 이번 확인한 HEAD: `16c0219 docs: Qdrant RAG 전체 회귀 검증 결과 반영`
- 회원 탈퇴 개인 문서 RAG 정리 구현·테스트·문서는 작업 트리에서 커밋 대기다. 사용자 선택 18개와 프로젝트 전체 564개 회귀 검증을 완료했다.

## 문서 갱신 규칙

- 현재 상태·다음 작업·최신 검증·Git 기준점은 이 문서에서 관리한다.
- 기능·정책·테스트 상세는 해당 문서에 반영하고 중요한 변경은 변경 이력에 날짜와 함께 추가한다.
- 테스트 결과는 실행 날짜·명령·성공 여부를 기록한다. 구현만 확인했다면 `구현됨, 검증 대기`로 표시한다.
- 과거 결과는 삭제하지 않는다. 과거 기록과 최신 상태가 다르면 이 문서의 최신 상태를 기준으로 읽는다.
