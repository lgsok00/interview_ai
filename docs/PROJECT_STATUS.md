# Interview AI Backend 프로젝트 현황

최종 갱신일: 2026-09-08

개발을 재개할 때 먼저 읽는 기준 문서다. 현재 상태와 다음 작업을 요약하고, 상세 내용과 과거 기록은 아래 문서에서 관리한다.

## 현재 상태

| 영역                             | 상태                                                                                       |
|----------------------------------|--------------------------------------------------------------------------------------------|
| 인증·회원 관리                   | 구현·검증 완료: JWT, Refresh Token, Google·GitHub 로그인, 회원정보·비밀번호 변경, 탈퇴     |
| 자기소개서                       | 구현·검증 완료: CRUD, 버전 이력·복원, 대표 설정                                            |
| 이력서                           | 구현·검증 완료: PDF 저장·검증·추출, CRUD·다운로드, 대표 설정                               |
| 기업·채용공고                    | 구현·검증 완료: 관리자 관리, 사용자 조회·검색, 관심 기업                                   |
| RAG 기반 모델                    | 구현·검증 완료: 원본 스냅샷·변환·접근 제어, metadata, 메모리 내 색인·삭제 작업 수명주기    |
| RAG 순번·등록 직렬화 기반        | 구현·검증 완료: V6·JPA 순번 저장·최초 생성 경합·행 잠금·롤백                               |
| RAG 작업 등록 영속화             | 구현·검증 완료: V7·UPSERT 스냅샷·DELETE 키·초기 PENDING 저장, 순번과 등록의 동시 커밋·롤백 |
| RAG CRUD 연결                    | 기업·채용공고 연결 및 MySQL 원자성·삭제/생성 경합 검증 완료, 자기소개서·이력서 연결 대기   |
| RAG 작업 실행·외부 연결          | 미구현: 실행 상태 전이 저장, worker·lease, 활성 generation·tombstone, Spring AI·Qdrant     |
| 운영 실행 기반                   | profile·컨테이너·health·OAuth2 proxy·로그·정리 scheduler 정책 검증 완료                    |
| 면접·평가·성장 분석 및 실제 배포 | 후속 범위                                                                                  |

## 다음 작업

1. 자기소개서·이력서 CRUD에 같은 트랜잭션 등록 정책을 연결하고 정상·경계·실패 시나리오를 검증한다.
2. 실행 상태 전이 저장과 worker 선점·lease, 활성 generation 교체, 삭제 tombstone을 구체화한다.
3. 이후 Spring AI·Qdrant 연결과 실제 색인·검색으로 진행한다.

자기소개서 PDF 업로드는 이력서의 파일 저장·검증·추출 기반을 공통화하는 별도 후속 작업이다. 실제 배포 환경 선정은 MVP 핵심 흐름 완성 이후 진행한다.

## 최신 검증

- 실행일: 2026-09-08
- 최신 사용자 전체 실행: Colima 소켓을 명시한 `./gradlew test --rerun-tasks` — BUILD SUCCESSFUL (1분 16초). XML 52개에서 전체 435개 성공,
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

| 문서                                         | 확인할 내용                                           |
|----------------------------------------------|-------------------------------------------------------|
| [구현 상세](status/IMPLEMENTATION.md)        | 실행 환경, 인증·사용자·자기소개서·이력서 API, DB 구조 |
| [기업·채용공고](status/CATALOG.md)           | API, 도메인 정책과 설계 결정, 단계별 과거 기록        |
| [자동 테스트 목록](status/TEST_COVERAGE.md)  | 테스트별 검증 범위                                    |
| [테스트 실행 기록](status/TEST_RESULTS.md)   | 실행 날짜·명령·성공·실패와 RAG 최신 검증 범위         |
| [개발 로드맵](status/ROADMAP.md)             | 전체 구현 순서와 후속 기능                            |
| [주요 결정과 확인 사항](status/DECISIONS.md) | API·인증·운영 정책과 남은 확인 사항                   |
| [변경 이력](status/HISTORY.md)               | 과거 변경사항과 분리 전 재개 메모                     |

## 실행 환경과 Git 기준점

- Java 21, Gradle Wrapper 9.5.1, Spring Boot 4.1.0, MySQL 8.4 및 Flyway
- 기본 profile: `local`, 기본 서버 포트: `8080`
- 브랜치: `main`
- 이번 검증 시 확인한 HEAD: `408ff7a feat: RAG 작업 등록 영속화 추가`
- 위 HEAD 이후 기업·채용공고 CRUD 연결, 공용 변경 등록 서비스, 신규 단위 테스트와 관련 테스트·문서가 커밋 대기 상태다. 기존 문서 수정도 보존했다.

## 문서 갱신 규칙

- 현재 상태·다음 작업·최신 검증·Git 기준점은 이 문서에서 관리한다.
- 기능·정책·테스트 상세는 해당 문서에 반영하고 중요한 변경은 변경 이력에 날짜와 함께 추가한다.
- 테스트 결과는 실행 날짜·명령·성공 여부를 기록한다. 구현만 확인했다면 `구현됨, 검증 대기`로 표시한다.
- 과거 결과는 삭제하지 않는다. 과거 기록과 최신 상태가 다르면 이 문서의 최신 상태를 기준으로 읽는다.
