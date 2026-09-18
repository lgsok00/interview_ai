# Interview AI Backend 프로젝트 현황

최종 갱신일: 2026-09-18

개발을 재개할 때 먼저 읽는 기준 문서다. 현재 상태와 다음 작업을 요약하고, 상세 내용과 과거 기록은 아래 문서에서 관리한다.

## 현재 상태

| 영역                   | 상태                                                                          |
|----------------------|-----------------------------------------------------------------------------|
| 인증·회원 관리             | 구현·검증 완료: JWT, Refresh Token, Google·GitHub 로그인, 회원정보·비밀번호 변경, 탈퇴           |
| 자기소개서                | 구현·검증 완료: CRUD, 버전 이력·복원, 대표 설정                                             |
| 이력서                  | 구현·검증 완료: PDF 저장·검증·추출, CRUD·다운로드, 대표 설정                                    |
| 기업·채용공고              | 구현·검증 완료: 관리자 관리, 사용자 조회·검색, 관심 기업                                          |
| 관리자 사용자 관리           | 구현·검증 완료: 목록·상세·역할·상태 변경, 인증 차단, 강제 삭제, 마지막 활성 관리자·RAG 정리 보호                |
| RAG 기반 모델            | 구현·검증 완료: 원본 스냅샷·변환·접근 제어, metadata, 메모리 내 색인·삭제 작업 수명주기                    |
| RAG 순번·등록 직렬화 기반     | 구현·검증 완료: V6·JPA 순번 저장·최초 생성 경합·행 잠금·롤백                                     |
| RAG 작업 등록 영속화        | 구현·검증 완료: V7·UPSERT 스냅샷·DELETE 키·초기 PENDING 저장, 순번과 등록의 동시 커밋·롤백            |
| RAG CRUD 연결          | 기업·채용공고·자기소개서·이력서의 UPSERT·DELETE 등록 연결 및 전체 회귀 검증 완료                        |
| RAG 작업 실행 기반         | 구현·검증 완료: V8·DB 선점·attempt·lease·재시도·worker·늦은 상태 변경 차단                     |
| RAG generation·삭제 차단 | 구현·검증 완료: V9·활성 generation 교체·DELETE tombstone·오래된 외부 쓰기 비활성화               |
| RAG 외부 색인            | 구현·전체 회귀 검증 완료: 700/100 token chunk·Processor·scheduler·Spring AI·Qdrant 연결 |
| RAG 외부 검색            | 구현·전체 회귀 검증 완료: 인증 범위 metadata·활성 generation·원본 존재/소유권·Top-K 5              |
| RAG 실제 외부 연동         | 검증 완료: 실제 OpenAI embedding·Qdrant 네트워크 UPSERT·개인 검색·DELETE smoke test       |
| 회원 탈퇴 RAG 정리         | 구현·검증 완료: 사용자 선행 잠금, 개인 문서 DELETE, flush·DB cascade, 커밋 후 파일 정리             |
| RAG 관리자 운영           | 구현·검증 완료: 원본·작업 조회, 실패 작업 재시도, 현재 원본 새 순번 재색인, 민감 내용 비노출                    |
| 운영 실행 기반             | profile·컨테이너·health·OAuth2 proxy·로그·정리 scheduler 정책 검증 완료                   |
| 면접 세션 생성             | 구현·검증 완료: 생성 API·대표 문서 자동 선택·현재 버전/추출 본문 스냅샷·미설정 생략                         |
| 면접 세션 영속 기반          | 구현·검증 완료: V10·세션/질문 스냅샷·상태 전이·질문 순서·원본 비의존·cascade                          |
| 면접 세션 내부 RAG 검색      | 구현·검증 완료: V11·정확한 세션 원본 범위·활성 generation·현재 접근 권한·Top-K 5                   |
| 초기 질문 생성·fallback    | 구현·검증 완료: V12·Chat 어댑터·5개 질문·재시도·lease·중복 저장 방지, 실제 Chat·RAG smoke 성공       |
| 면접 조회·진행 API         | 구현·검증 완료: 소유자 목록·상세·질문 제공, 시작·완료 상태 전이, 생성 실패 수동 재시도                        |
| 면접 답변·꼬리 질문          | 구현·검증 완료: V13·답변 저장/조회·답변 기반 질문·멱등 재전송·동시성, 실제 Chat smoke 성공                |
| 답변 AI 평가·개선사항 저장     | 구현·검증 완료: 평가 38개·HTTP·설정·scheduler 전체 회귀 및 실제 Chat smoke 성공                 |
| 면접 결과·성장 분석          | 구현·검증 완료: V15 완료 시각·결과 상세·기간/직무/기업/공고 통계·강약점·변화량·학습 로드맵                     |
| 외부 수집 영속 기반          | 구현·검증 완료: V18/V19 요청·불변 스냅샷·상태/lease·수동 재시도·최대 9회 이력. 실제 수집·승인 API 보류       |
| AI 자기소개서 초안          | 계약 확정, 미구현: 입력 스냅샷·생성 작업·검수/재생성·명시 적용                                       |

## 다음 작업

1. V20 AI 자기소개서 초안의 입력 스냅샷·생성 작업·검수/재생성·명시 적용을 구현한다.
2. 외부 실제 수집·관리자 승인 API는 공식 API 또는 수집 허가를 받은 데이터 소스가 정해질 때 재개한다.
3. STT/TTS·실시간 면접·PDF 리포트는 MVP 이후 확장 범위다.

자기소개서 PDF 업로드는 이력서의 파일 저장·검증·추출 기반을 공통화하는 별도 후속 작업이다. 실제 배포 환경 선정은 MVP 핵심 흐름 완성 이후 진행한다.

외부 수집·AI 문서 초안의 세부 계약은 [외부 수집·AI 문서 초안 계약](status/COLLECTION_AND_DRAFT.md)을 기준으로 한다. 외부 수집은 영속 기반까지만 검증했고 네트워크 수집·검수 승인
연결은
보류했다. AI 자기소개서 초안은 아직 구현하지 않았다.

## 최신 검증

- 실행일: 2026-09-18 — 외부 수집 V18/V19 영속 기반 선택 및 전체 회귀 성공.
- 사용자 선택 실행: `.\gradlew.bat test --tests "com.interviewai.externalcollection.*"` — BUILD SUCCESSFUL (27초), XML 3개·17개
  성공, 실패·오류·건너뜀 0.
- 사용자 전체 실행: `.\gradlew.bat test` — BUILD SUCCESSFUL (5분 35초). 최신 XML 104개·951개 성공, 실패·오류·건너뜀 0.
- 요청 생성·선점·lease·늦은 실행 차단·수동 재시도, 성공/실패 스냅샷, V18/V19·JSON 저장·9회 이력·DB 제약을 검증했다. 실제 외부 네트워크 호출은 포함하지 않는다.

- 실행일: 2026-09-17 — 관리자 사용자 정지·강제 삭제 선택 및 전체 회귀 성공.
- 사용자 선택 실행: 관리자 사용자·인증·보안 관련 XML 20개·161개 성공, 실패·오류·건너뜀 0.
- `AdminRagIntegrationTest` 재실행 BUILD SUCCESSFUL (36초), 21개 성공. 공통 삭제 서비스 도입 후 회원 탈퇴 RAG 정리 회귀를 확인했다.
- 사용자 전체 실행: `.\gradlew.bat test` — BUILD SUCCESSFUL (5분 41초). 최신 XML 101개·934개 성공, 실패·오류·건너뜀 0.
- V17 상태 제약, 로컬·OAuth2·Refresh Token·기존 Access Token 차단, 관리자 정지·복구·강제 삭제, 마지막 활성 관리자와 개인 문서 RAG·파일 정리를 검증했다.

- 실행일: 2026-09-17 — 관리자 RAG·UserService 선택 실행 BUILD SUCCESSFUL (32초).
- 사용자 실행:
  `.\gradlew.bat test --tests "com.interviewai.rag.service.AdminRag*" --tests "com.interviewai.rag.controller.AdminRagControllerTest" --tests "com.interviewai.user.service.UserServiceTest"`.
- 선택 범위 XML 8개·98개 성공, 실패·오류·건너뜀 0: 관리자 RAG 80개와 UserService 18개다.
- 사용자 전체 실행: `.\gradlew.bat test` — BUILD SUCCESSFUL (4분 45초). 최신 XML 100개·916개 성공, 실패·오류·건너뜀 0.
- 원본·작업 조회와 개인 문서 내용 비노출, 같은 작업 재시도, 새 순번 재색인, V16, rollback·generation·동시성 및 회원 탈퇴 잠금·cascade·파일 정리를 검증했다.
- Codex는 테스트를 실행하지 않고 사용자 출력·최신 XML·실제 변경사항을 확인했다. OpenJDK class-data sharing 경고는 결과에 영향을 주지 않았다.
- 아래 실패 기록은 수정 과정의 진단 이력이다.

- 실행일: 2026-09-17 — 관리자 RAG·UserService 선택 재실행 XML 8개·98개 중 94개 성공, 4개 실패, 오류·건너뜀 0.
- 명령:
  `.\gradlew.bat test --tests "com.interviewai.rag.service.AdminRag*" --tests "com.interviewai.rag.controller.AdminRagControllerTest" --tests "com.interviewai.user.service.UserServiceTest"`.
- 관리자 RAG 80개는 전부 성공했다. 실패 4개는 회원 탈퇴의 사용자 잠금·존재 검사 호출이 빠져 발생했다. 해당 호출 복원과 전체 회귀는 대기다.
- 삭제 경합 통합 테스트는 준비 트랜잭션에서 사용자 잠금을 먼저 잡으므로 이 성공만으로 UserService 내부의 선행 잠금이 검증됐다고 판단하지 않는다.
- 아래 79개 실행은 수정 전 기록이다.

- 실행일: 2026-09-17 — 관리자 RAG 선택 실행 BUILD FAILED (45초). XML 4개·79개 중 73개 성공, 6개 실패, 오류·건너뜀 0.
- 실행 명령:
  `.\gradlew.bat test --tests "com.interviewai.rag.service.AdminRag*" --tests "com.interviewai.rag.controller.AdminRagControllerTest"`.
- 통합 테스트의 MANDATORY 프록시 stubbing 실패 2개와 후속 오염 2개, 문서를 로드한 상태의 사용자 JPA 삭제 실패 2개를 확인했다.
- 실제 spy에 stubbing하도록 수정했고 실제 UserService를 통한 삭제 경합·롤백 회귀를 준비했다. 애플리케이션 삭제 수정과 재실행은 대기다. Codex는 테스트를 실행하지 않았다.
- 아래 전체 836개 성공은 이번 변경 전 검증 기준이다.

- 실행일: 2026-09-17 — 관리자 사용자 관리 MySQL 통합 및 전체 회귀 성공.
- 사용자 선택 실행: `.\gradlew.bat test --tests "com.interviewai.user.service.AdminUserServiceIntegrationTest"` — BUILD
  SUCCESSFUL (30초), 4개 성공.
- 사용자 전체 실행: `.\gradlew.bat test` — BUILD SUCCESSFUL (4분 21초). 최신 XML 96개·836개 성공, 실패·오류·건너뜀 0.
- 실제 MySQL의 대소문자 비구분 이메일·닉네임 검색, 역할·provider 필터, LIKE escape, 안정적 페이지 정렬, 관리자 행 잠금 대기와 두 관리자의 동시 상호 강등 시 1명 보존을 검증했다.
- 기존 서비스 16개·컨트롤러 13개를 포함한 관리자 사용자 관리와 프로젝트 전체 회귀가 완료됐다. OpenJDK class-data sharing 경고는 결과에 영향을 주지 않았다.

- 실행일: 2026-09-16 — 면접 결과·성장 분석 구현 및 전체 회귀 성공.
- 사용자 선택 실행: `InterviewAnalysisServiceTest`, `InterviewAnalysisControllerTest`, `InterviewSessionServiceTest`,
  `InterviewSessionControllerTest` — BUILD SUCCESSFUL (35초).
- 사용자 전체 실행: `.\gradlew.bat test` — BUILD SUCCESSFUL (6분 11초). 최신 XML 93개·803개 성공, 실패·오류·건너뜀 0.
- V15 완료 시각, 소유자 결과 조회, 완료/부분/대기 분석 상태, 완료 평가만의 점수 집계, 질문 유형별 통계, 90일 기본·365일 제한과 직무/기업/공고 필터, 최근/이전 세션
  변화량, 표본 기반 강점·약점과 결정적 학습 로드맵을 검증했다.
- 두 세션 이상에서 이전 그룹이 비던 변화량 분할을 최근 최대 3개와 그 직전 최대 3개로 수정했다. Codex는 테스트를 직접 실행하지 않고 사용자 출력·최신 XML·실제 변경사항을 확인했다.
- 아래는 직전 실제 외부 연동 검증 기록이다.

- 실행일: 2026-09-16 — 실제 OpenAI Chat·RAG 면접 전체 흐름 smoke test 성공.
- 사용자 실행: `./scripts/chat-smoke.ps1 -Email "smoke@example.com" -JobPostingId 1` — `PASS`.
- 세션 3에서 AI 초기 질문 5개, AI 꼬리 질문 1개, 답변 평가 2의 `COMPLETED`, STAR 18·논리성 32·직무 적합성 28, 세션 `COMPLETED`를 확인했다.
- 테스트용 기업·채용공고는 관리자 API로 생성·수정해 RAG UPSERT를 등록했다. Windows PowerShell 5.1 호환을 위해 smoke 스크립트에 UTF-8 BOM·UTF-8 JSON
  전송·SecureString 변환·배열 정규화를 적용했다. Codex는 테스트를 직접 실행하지 않고 사용자 출력을 확인했다.
- 아래는 직전 자동 회귀 검증 기록이다.

- 실행일: 2026-09-16 — 답변 평가 HTTP·설정·scheduler 테스트 보강 및 전체 회귀 성공.
- 사용자 선택 실행:
  `.\gradlew.bat test --tests "com.interviewai.interview.controller.AnswerEvaluationControllerTest" --tests "com.interviewai.interview.evaluation.*"` —
  BUILD
  SUCCESSFUL (29초).
- 사용자 전체 실행: `.\gradlew.bat test` — BUILD SUCCESSFUL (4분 26초). 최신 XML 91개·793개 성공, 실패·오류·건너뜀 0. 면접 26개 클래스·219개,
  답변 평가 7개 클래스·38개 성공이다.
- 평가 접수·조회·재시도 HTTP 계약과 인증·오류 응답, 안전한 기본 설정·명시 설정·외부 환경 격리·경계 검증, scheduler의 지연 설정·큐 소진·처리 한도·예외·interrupt를 확인했다.
- 당시 실제 평가 Chat 네트워크 호출은 미검증이었으며 위 smoke에서 검증을 완료했다. 아래는 이전 검증 기록이다.

- 실행일: 2026-09-16 — Refresh Token 정리 설정 테스트의 외부 설정 격리 후 전체 회귀 성공.
- 사용자 단건 실행:
  `.\gradlew.bat test --tests "com.interviewai.auth.scheduler.RefreshTokenCleanupSchedulerConfigurationTest"` — BUILD
  SUCCESSFUL (11초).
- 사용자 전체 실행: `.\gradlew.bat test` — BUILD SUCCESSFUL (4분 42초). 최신 XML 88개·777개 성공, 실패·오류·건너뜀 0. 정리 설정 6개와 기존 평가 22개를
  포함한다.
- 직전 전체 실행은 775개 중 prod 기본 비활성화 검사 1개 실패였다. 테스트 컨텍스트의 환경변수·JVM 속성·외부 설정 파일 유입을 격리하고 회귀 2개를 추가했다. 당시 유입된 정확한 외부 값은 확정하지
  않았다.
- 운영 설정은 변경하지 않았다. 실제 평가 Chat 호출과 평가 HTTP·설정·scheduler 추가 테스트는 미검증 범위로 유지한다.
- 아래는 이전 검증 기록이다.

- 실행일: 2026-09-15 — 사용자
  `.\gradlew.bat test --tests "com.interviewai.interview.entity.InterviewAnswerEvaluationTest" --tests "com.interviewai.interview.evaluation.*"`
  재실행 BUILD SUCCESSFUL (29초).
- 최신 XML 4개·22개 성공, 실패·오류·건너뜀 0: 엔티티 4개, generator 8개, worker 5개, MySQL 통합 5개.
- 선점 SQL의 `status - 'PENDING'` 오타를 `status = 'PENDING'`으로 수정하고 worker 실패 처리 후 `true` 반환을 확인했다. HTTP·설정·scheduler 보강, 전체
  회귀 및 실제 평가 Chat 호출은 남아 있다.
- 아래는 이전 선택 검증 기록이다.

- 실행일: 2026-09-15 — 사용자 `.\gradlew.bat test --tests "com.interviewai.interview.entity.InterviewAnswerEvaluationTest"` 실행
  BUILD SUCCESSFUL (14초).
- 실제 XML에서 엔티티 4개 성공, 실패·오류·건너뜀 0 확인. API·AI 실행부 전체의 검증 완료를 뜻하지 않는다.
- 추가 generator·worker·MySQL 통합 테스트는 작성됨, 실행 대기. 실제 평가 Chat 호출은 미검증이다.

- 실행일: 2026-09-15 — 답변 저장·조회 및 답변 기반 꼬리 질문 전체 회귀 검증 완료.
- 사용자 면접 선택 실행: `.\gradlew.bat test --tests "com.interviewai.interview.*"` — BUILD SUCCESSFUL (1분 11초).
- 사용자 전체 실행: `.\gradlew.bat test` — BUILD SUCCESSFUL (4분 10초). XML 84개·753개 성공, 실패·오류·건너뜀 0. 면접 XML 19개·181개, 신규 5개
  클래스·49개 성공.
- V13·길이 경계·소유권·재전송·완료 후 쓰기 차단·AI 응답/timeout·cascade·롤백 및 MySQL 반복 읽기에서 동시 답변/꼬리 질문/순번 할당을 검증했다.
- 실제 Chat 네트워크 호출은 미검증이다. Codex는 테스트를 실행하지 않고 사용자 출력과 최신 XML을 확인했다.

- 실행일: 2026-09-15 — 면접 조회·시작·완료·질문 제공 API와 생성 실패 수동 재시도 endpoint 전체 회귀 검증 완료.
- 사용자 면접 선택 실행: `.\gradlew.bat test --tests "com.interviewai.interview.*"` — BUILD SUCCESSFUL (1분 3초).
- 사용자 전체 실행: `.\gradlew.bat test` — BUILD SUCCESSFUL (3분 50초). XML 79개·704개 성공, 실패·오류·건너뜀 0. 면접 XML 14개·132개 성공.
- 소유자별 목록·상세, 안정적인 최신순 정렬, 질문 순서·노출 범위, 비관적 잠금 상태 전이, 소유권 은닉, 상태 충돌, 비인증과 HTTP 202 수동 재시도를 검증했다.
- OpenJDK class-data sharing 경고는 결과에 영향을 주지 않았다.

- 실행일: 2026-09-14 — 초기 질문 생성·fallback·실행 정책 전체 회귀 검증 완료.
- 설정 테스트 재실행: `.\gradlew.bat test --tests "com.interviewai.interview.generation.InterviewGenerationConfigurationTest"` —
  BUILD SUCCESSFUL (7초).
- 사용자 전체 실행: `.\gradlew.bat test` — BUILD SUCCESSFUL (4분 14초). XML 79개·683개 성공, 실패·오류·건너뜀 0. 면접 XML 14개·111개, 신규
  generation 7개 클래스·63개 성공.
- 최초 면접 선택 실행의 설정 테스트 2개 실패는 외부 설정 유입 때문이었다. 테스트 컨텍스트에서 환경변수·JVM 속성을 격리하고 명시적 테스트 설정 유지까지 검증했다.
- V12·질문 전체 저장/READY 원자성·동시 선점/완료/수동 재시도·lease 만료·DB 롤백을 검증했다. Chat·RAG 호출은 mock이며 실제 Chat API 네트워크 연동은 미검증이다.
- 아래는 이전 검증 기록이며 상세 실행 경위는 [테스트 실행 기록](status/TEST_RESULTS.md)을 참고한다.

- 실행일: 2026-09-14
- V11로 세션에 원본 FK 없는 `company_id`를 추가하고, 기업·공고·선택 자기소개서·선택 이력서의 정확한 원본 키만 허용하는 내부 RAG 검색 계약을 구현했다.
- Qdrant exact-key metadata filter 뒤에도 허용 집합, 활성 generation, 현재 원본 존재·소유권을 재검사하며 빈 결과는 정상 반환하고 검색 기반 장애는 전파한다.
- 사용자 선택 실행: `.\gradlew.bat test --tests "com.interviewai.rag.*" --tests "com.interviewai.interview.*"` — BUILD
  SUCCESSFUL (1분 34초).
- 사용자 전체 실행: `.\gradlew.bat test` — BUILD SUCCESSFUL (3분 38초). XML 72개에서 전체 618개 성공, 실패·오류·건너뜀 0을 확인했다. RAG·면접 XML은
  31개·304개다.
- OpenJDK class-data sharing 경고는 결과에 영향을 주지 않았다.
- 실행일: 2026-09-12
- `POST /api/interview-sessions`는 채용공고 ID를 필수로 받고 인증 사용자의 대표 자기소개서·대표 이력서를 자동 선택해 생성 시점 스냅샷과 `GENERATING` 세션을 저장한다.
- 대표 문서 미설정은 선택 입력으로 생략하고, 자기소개서는 현재 버전, 이력서는 추출 완료 본문을 사용한다. 대표 이력서가 추출 대기·실패이거나 본문이 비어 있으면 HTTP 409
  `REPRESENTATIVE_RESUME_NOT_READY`로 거부한다.
- 사용자 선택 실행: `./gradlew test --tests "com.interviewai.interview.*"` — BUILD SUCCESSFUL (21초).
- 사용자 전체 실행: `./gradlew test` — BUILD SUCCESSFUL (2분 15초). XML 70개에서 전체 603개 성공, 실패·오류·건너뜀 0을 확인했다. 면접 테스트는 6개 클래스 39개다.
- OpenJDK class-data sharing 경고는 결과에 영향을 주지 않았다.
- 실행일: 2026-09-12
- V10으로 면접 세션·질문 테이블을 추가하고, 원본 삭제 이후에도 과거 면접을 재현하도록 기업·공고·선택 문서의 생성 시점 스냅샷을 저장한다. 사용자 삭제와 세션 삭제에는 DB cascade를 적용한다.
- 세션 상태 `GENERATING → READY → IN_PROGRESS → COMPLETED`, 생성 실패·재시도, 질문 유형·생성 출처·순서와 RAG context 스냅샷 모델을 구현했다.
- 사용자 선택 실행: `./gradlew test --tests "com.interviewai.interview.*"` — BUILD SUCCESSFUL (27초).
- 사용자 전체 실행: `./gradlew test` — BUILD SUCCESSFUL (2분 15초). XML 67개에서 전체 589개 성공, 실패·오류·건너뜀 0을 확인했다. 면접 테스트는 3개 클래스 25개다.
- OpenJDK class-data sharing 경고는 결과에 영향을 주지 않았다.
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
- 이번 확인한 HEAD: `944661e feat(rag): 관리자 운영 조회와 재시도·재색인 추가`
- 관리자 사용자 정지·강제 삭제 구현·테스트·검증 문서가 작업 트리에서 커밋 대기다. 최신 전체 검증은 101개 클래스·934개 성공이다.

## 문서 갱신 규칙

- 현재 상태·다음 작업·최신 검증·Git 기준점은 이 문서에서 관리한다.
- 기능·정책·테스트 상세는 해당 문서에 반영하고 중요한 변경은 변경 이력에 날짜와 함께 추가한다.
- 테스트 결과는 실행 날짜·명령·성공 여부를 기록한다. 구현만 확인했다면 `구현됨, 검증 대기`로 표시한다.
- 과거 결과는 삭제하지 않는다. 과거 기록과 최신 상태가 다르면 이 문서의 최신 상태를 기준으로 읽는다.
