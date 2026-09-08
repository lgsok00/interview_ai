# 자동 테스트 목록

[프로젝트 현황으로 돌아가기](../PROJECT_STATUS.md)

테스트별 검증 범위를 보존한 상세 목록이다. 실행 날짜·명령·결과는 [테스트 실행 기록](TEST_RESULTS.md)을 참고한다.

### 작성된 자동 테스트

#### RAG 활성 generation·DELETE tombstone — 검증 완료 (2026-09-08)

- `RagIndexSourceTest` 10개: 기존 순번·overflow와 함께 활성 generation 최초 게시·교체·유지, DELETE 무효화, tombstone 이전 순번 거부, 잘못된 입력의 상태 보존과
  최대 순번 경계를 검증한다.
- `RagClaimedJobGenerationTest` 2개: attempt UUID generation, 같은 실행·chunk의 결정적 point ID, 작업·attempt·chunk 간 분리, DELETE와
  음수 chunk 거부를 검증한다.
- `RagIndexJobExecutionServiceTest` 23개: 기존 실행 입력 경계에 활성 generation 조회 null 입력과 Repository 미호출 검증을 추가했다.
- `RagActiveGenerationMigrationIntegrationTest` 1개: V8 상태에서 V9로 올릴 때 작업 상태와 관계없이 최신 DELETE 순번을 tombstone으로 이관하고 활성
  generation을 임의 추정하지 않는지 실제 MySQL에서 검증한다.
- `RagActiveGenerationIntegrationTest` 25개: 최초·교체 게시, 새 작업 대기·실패 시 기존 generation 유지, 최신/과거 완료 순서 역전, DELETE 즉시 무효화와 이후
  재활성화, 반복 DELETE, 잘못된·만료·재선점 attempt 차단을 검증한다.
- 같은 통합 테스트에서 원본별 활성 조회, 같은 트랜잭션의 DELETE→UPSERT, 최초/기존 원본 롤백, 작업 INSERT 실패, 활성화 DB 실패 시 JDBC 성공 롤백, V9 CHECK, DELETE
  등록·완료 동시성과 잠금 대기 중 lease 만료를 검증한다.
- 신규 테스트는 새 클래스 3개 28개, 기존 엔티티 8개, 실행 서비스 1개로 합계 37개다. RAG 17개 클래스 216개와 전체 58개 클래스 528개가 성공했으며 실패·오류·건너뜀은 0이다.

#### RAG 실행 기반 — 검증 완료 (2026-09-08)

- `RagIndexJobExecutionServiceTest`: 정상 실패 코드·100자 경계, null·빈 값·잘못된 문자·길이 초과, lease·재시도 설정과 작업 ID·attempt 입력 검증. 실패 코드
  정규식 오타를 잡는 회귀 테스트를 포함한다.
- `RagIndexJobWorkerTest`: 빈 큐·null 처리기, 처리 순서·lease 연장 콜백, 늦은 완료·실패, 예외의 고정 코드 변환, 인터럽트 플래그 복원, DB 오류 전파, 치명적 Error의
  lease 복구 위임.
- `RagIndexJobExecutionServiceIntegrationTest`: 실제 MySQL에서 V8 CHECK, UPSERT 스냅샷·DELETE 복원, 성공·버전 증가, 잘못된 attempt·만료
  lease 거부, lease 연장·재선점, 재시도 지연·소진, 미래 대기 작업 건너뛰기, 트랜잭션 필수·롤백, 동시 선점·SKIP LOCKED, worker의 트랜잭션 밖 처리와 독립 커밋, 실제 실패 저장
  경로.
- 시간 경계는 DB UTC 시각으로 lease·available_at을 조정해 검사하며 sleep으로 만료를 기다리지 않는다. 동시성 검증은 barrier·latch와 제한 시간으로 조정한다.
- 위 3개 클래스 51개는 사용자 선택 실행과 전체 실행에서 모두 성공했다. 전체 XML 55개에서 491개 성공, 실패·오류·건너뜀 0을 확인했다.

`RagIndexJobEntityTest` 6개와 `RagIndexJobRegistrationServiceIntegrationTest` 40개가 2026-09-07 전체 실행에서 성공했다.

#### 기업·채용공고 RAG 등록 연결

- `RagSourceChangeRegistrationServiceTest` 7개: 네 원본 유형의 UPSERT/DELETE 분기, `rag-v1`, 최대 3회 시도, null 이력서 실패를 검증한다.
- `CatalogApiTest`: 기업·채용공고 생성·수정·삭제가 공용 RAG 등록 서비스를 호출하고 생성 시 `saveAndFlush`로 ID를 확정하는 흐름을 검증한다.
- `CatalogConcurrencyIntegrationTest`: 비관적 읽기 잠금으로 기업 삭제·공고 생성 경합을 직렬화하고 고아 공고와 FK 오류가 남지 않는지 실제 MySQL에서 검증한다.

- 엔티티: 순번·최대 실행 횟수의 0/음수 거부, 필수값 거부, 최대 순번 허용·마이크로초 시간 정밀도 (6개)
- MySQL: V7 적용, 네 원본 유형별 UPSERT 저장·복원 및 DELETE 등록 (9개)
- 같은 트랜잭션의 UPSERT·DELETE 연속 등록, 다른 유형·ID의 독립 순번 (2개)
- 트랜잭션 없는 호출, 잘못된 최대 실행 횟수, null 입력 거부 (4개)
- 신규 원본·작업 롤백, 기존 순번·버전·복수 작업 롤백, 실제 INSERT unique 충돌 시 순번 롤백 (3개)
- 신규·기존 원본의 동시 등록 (2개)
- 순번·상태·횟수·버전·시간·본문 CHECK 제약 (15개), 관리 행 FK (1개), 공고·개인 문서의 필수 소유 메타데이터 (3개), overflow 시 값 보존 (1개)
- UPSERT 재조회에는 긴 한글·이모지 본문과 100자 pipeline version, 초기 상태·UTC 시각 확인을 포함한다.

위 테스트는 등록 영속화를 검증하며 기존 CRUD 연동, 실행 상태 전이 저장, worker·lease·외부 색인 실행은 포함하지 않는다.

`RagIndexSourceTest`에 순번 증가와 최대 순번 초과 시 엔티티 값 보존 2개 시나리오가 작성되어 있다.

`RagIndexSequenceServiceIntegrationTest`에 다음 MySQL 시나리오 15개가 작성되어 있다 (2026-09-07 전체 실행 성공).

- V6 migration 적용
- 별도 트랜잭션 사이 순번·버전 영속화와 관리 행 단일성
- 같은 트랜잭션의 연속 순번 발급
- 유형 또는 ID가 다른 원본의 독립 순번
- 호출자 트랜잭션 부재 및 null 원본 키 거부 (2개)
- 신규 관리 행 및 flush된 기존 순번·버전 롤백 (2개)
- 최대 순번 허용 및 overflow 시 DB 값 보존
- 신규·기존 원본의 독립 트랜잭션 동시 등록 (2개)
- 잘못된 유형·0 ID·음수 순번·음수 버전의 MySQL CHECK 제약 (4개)

위 순번 서비스 테스트 자체에는 작업 저장·worker·lease·외부 색인 동시성 검증을 포함하지 않는다. 작업 저장 검증은 앞의 등록 서비스 테스트에서 다룬다.

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

`CoverLetterServiceTest`에 다음 14개 시나리오가 작성되어 있다.

- 자기소개서와 초기 버전 생성 및 RAG UPSERT 등록
- 존재하지 않는 사용자의 생성 거부
- 목록의 대표 자기소개서 구분
- 현재 버전 상세 조회
- 다른 사용자 자기소개서 은닉
- 수정 시 비관적 잠금, 새 버전 생성과 RAG UPSERT 등록
- 삭제 시 RAG DELETE 선등록과 원본 삭제 순서
- RAG DELETE 등록 실패 시 원본 미삭제
- 존재하지 않는 버전 조회 거부
- 과거 버전을 새 버전으로 복원하고 RAG UPSERT 등록
- 대표 자기소개서 신규 설정과 교체
- 미설정 대표 자기소개서 조회 거부
- 잘못된 JWT subject 거부

`CoverLetterControllerTest`에 다음 14개 시나리오가 작성되어 있다.

- 자기소개서 생성 시 HTTP 201과 응답 검증
- 제목 앞뒤 공백 제거와 본문 공백 보존
- 빈 제목·본문 validation
- 제목 100자와 본문 20,000자 경계 허용 및 초과 거부
- JWT 없는 요청의 HTTP 401
- 자기소개서 목록·수정·삭제
- 소유하지 않은 자기소개서의 `COVER_LETTER_NOT_FOUND`
- 존재하지 않는 버전의 `COVER_LETTER_VERSION_NOT_FOUND`
- 과거 버전 복원
- 대표 자기소개서 설정·해제
- 대표 자기소개서 미설정 오류

`CoverLetterRepositoryIntegrationTest`에 다음 6개 MySQL 통합 시나리오가 작성되어 있다.

- Flyway V3 migration 적용
- 여러 버전 저장과 버전 번호 내림차순 조회
- 사용자당 하나의 대표 자기소개서 유지 및 교체
- 다른 사용자 문서의 대표 설정을 복합 외래 키로 거부
- 자기소개서 삭제 시 버전과 대표 설정 cascade 삭제
- 사용자 삭제 시 자기소개서와 버전 및 대표 설정 cascade 삭제

`ResumeServiceTest`에 다음 11개 시나리오가 작성되어 있다.

- 추출 완료 PDF 저장·이력서 생성과 RAG 변경 등록
- 추출 실패 이력서의 RAG 상태 정리 등록
- 존재하지 않는 사용자의 생성 거부와 RAG 미등록
- 다른 사용자 이력서 은닉
- PDF 교체 시 신규 파일 롤백 정리·기존 파일 커밋 후 정리와 RAG 변경 등록
- 제목 수정 후 RAG 변경 등록
- 삭제 시 RAG DELETE·DB 삭제·파일 정리 예약 순서
- RAG DELETE 등록 실패 시 DB·파일 미삭제
- 대표 이력서 설정
- 미설정 대표 이력서 조회 거부
- 잘못된 JWT subject 거부

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
