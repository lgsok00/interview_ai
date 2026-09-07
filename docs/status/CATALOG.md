# 기업·채용공고 구현과 결정

[프로젝트 현황으로 돌아가기](../PROJECT_STATUS.md)

2026-09-07 API 구현·검증 완료. 아래 2026-09-04 당시 코드·검증 대기 기록은 이력으로 보존하며, 현재 미구현 항목을 뜻하지 않는다.

## 기업·채용공고 개발 진행 상황 (2026-09-04)

현재 V5·엔티티·Repository·공통 기반·DTO·Service·Controller·전역 예외가 반영되었으며, 2026-09-07 전체 307개 테스트 성공으로 기업·채용공고 API 구현 및 검증을 완료했다.

### 2026-09-04 당시 반영 코드 (과거 기록)

- 1단계: `V5__create_companies_and_job_postings.sql` 작성. `companies`, `job_postings`, `company_favorites`와
  FK·unique·CHECK·조회 인덱스를 정의함. DB migration 실행은 미확인.
- 2단계: `Company`, `JobPosting`, `CompanyFavorite` 엔티티와 `EmploymentType`, `JobPostingStatus` enum 작성.
- `JobPosting.statusAt(now)`에 수동 마감·종료 시각·시작 시각 순서의 상태 계산 구현. `update()`의 `updatedAt = now` 누락 수정 반영 확인.
- 3단계: `CompanyRepository`, `CompanyFavoriteRepository`, `JobPostingRepository` 작성.
- 기업명 검색과 관심 여부 projection, 관심 기업 목록·중복 등록 upsert·삭제 쿼리 작성.
- 공고 제목·기업·상태 필터, 목록 projection, 기업 fetch join 상세 조회, 기업·공고 비관적 잠금 조회 작성.
- `CompanyRepository.search()`의 `userId`가 `Long`으로 수정된 것을 확인함.
- 4-1단계 공통 기반: `CatalogException`, `CatalogInput`, `CatalogTimeConfig`, `AdminAuthorizationService` 작성.
- `CatalogInput`에 문자열 정규화·길이, HTTP/HTTPS URL, 양의 ID, 페이지 범위, 검색어 escape, UTC·마이크로초 변환과 모집 기간 검증 구현.
- `CatalogTimeConfig`에 `catalogClock` UTC Bean 등록. `AdminAuthorizationService`에 subject 형식·DB 사용자 존재·DB의 ADMIN 역할 확인 구현.
- `CatalogException.errors` 필드와 private 생성자의 매개변수가 모두 `Map<String, String>`으로 수정되어 기존 `ErrorResponse`와 타입이 일치하는 것을 확인함. 컴파일·테스트 검증은 아직 수행하지 않음.
- DTO, Service, Controller, 신규 예외 매핑은 아직 반영되지 않음. 관리자 검사 메서드는 작성되었으나 실제 API 호출 경로에는 아직 연결되지 않음.

### 이후 구현에서 유지할 결정

- 기업·공고는 로그인 사용자가 조회하는 공용 데이터다. 관리자가 수동 등록·수정·삭제하며 외부 수집·크롤링·AI 분석은 후속 범위로 둔다.
- 일반 사용자 기능은 기업 검색·상세·관심 등록·해제·목록과 공고 목록·상세·기업별 조회다. 관심 공고·지원 현황·첨부 파일·로고 업로드는 이번 범위에서 제외한다.
- 상세 필드와 길이·DB 제약의 기준은 V5와 엔티티다. 기업명·홈페이지·공고 제목의 중복은 허용한다. 기업 소개는 20,000자, 공고 본문은 30,000자 이하의 일반 텍스트다.
- 기업과 공고는 1:N이며 공고 소속 기업은 수정하지 않는다. 공고가 남아 있는 기업 삭제는 FK RESTRICT와 409 `COMPANY_HAS_JOB_POSTINGS`로 제한한다.
- 기업·공고는 hard delete한다. 회원 또는 기업 삭제 시 관심 설정은 DB cascade로 삭제하며 회원 탈퇴로 공용 기업·공고는 삭제하지 않는다. 면접 세션 연계 전에 삭제 정책과 당시 내용
  snapshot 보존을 재검토한다.
- 관심 기업은 `(user_id, company_id)` unique와 upsert로 중복 등록을 처리하고 기존 등록 시각을 유지한다. 해제는 미등록·삭제된 기업에 대해서도 멱등하게 처리한다.
- 상태는 저장하지 않는다. 수동 마감 또는 `now >= closesAt`이면 CLOSED, 그 외 `now < opensAt`이면 SCHEDULED, 나머지는 OPEN이다. NULL 시각은 해당 제한 없음으로
  취급한다.
- 기본 조회는 모든 상태를 포함하며 예정·마감 공고도 상세 조회를 허용한다. 목록 상태·필터·count는 동일한 기준 시각을 사용하고 별도 마감 scheduler는 두지 않는다.
- 신규 도메인의 시각은 Service의 UTC Clock에서 생성해 엔티티에 전달한다. API는 offset 포함 시각을 사용하고 UTC·마이크로초 정밀도로 저장한다. 두 모집 시각이 있으면 시작 < 종료여야 하며
  과거 공고도 등록 가능하다.
- 문자열은 앞뒤 공백 제거 후 검증하고 선택 문자열의 빈 값은 NULL로 변환한다. URL은 host를 포함한 절대 HTTP/HTTPS 주소로 제한하며 서버에서 가져오지는 않는다.
- 수정은 PUT 전체 교체다. 필수 값 누락은 400, 선택 값 누락·NULL은 기존 값 삭제다. 공고 생성의 수동 마감 기본값은 false이며 수정에서는 값을 명시한다.
- 모든 Service 진입점은 JWT subject로 DB 사용자 존재를 확인한다. 관리자 쓰기는 DB의 최신 ADMIN 역할을 검사하며 JWT role claim만으로 허용하지 않는다. 공개 관리자 가입 API는
  추가하지 않는다.
- 쓰기 트랜잭션에서는 기업 잠금 → 공고 잠금 순서를 유지한다. 실제 FK 위반 변환은 정확한 제약과 원인을 구분하고 모든 무결성 오류를 기업 삭제 충돌로 처리하지 않는다.
- keyword는 최대 100자, 공백 제거 후 빈 값이면 전체 조회다. 기업명·공고 제목 부분 일치 검색을 사용하고 `!`, `%`, `_`를 escape한다. 공고에는 companyId·status 필터를
  제공한다.
- page 기본 0·0 이상, size 기본 20·1~100. 일반 목록은 `createdAt DESC, id DESC`, 관심 목록은 관심 등록 시각·id 내림차순이다. 관심 목록에는 별도 Pageable 정렬을
  추가하지 않는다.
- API 목록은 본문을 제외하고 `items`, `page`, `size`, `totalElements`, `totalPages`의 도메인별 DTO로 반환한다. 기업 요약에는 관심 여부, 공고 요약에는 기업
  id·이름과 모집 상태·기간을 포함한다.

### 구현된 API와 오류

- `GET /api/companies`, `GET /api/companies/{companyId}`, `GET /api/companies/{companyId}/job-postings`
- `GET /api/companies/favorites`, `PUT /api/companies/{companyId}/favorite`,
  `DELETE /api/companies/{companyId}/favorite`
- `GET /api/job-postings`, `GET /api/job-postings/{jobPostingId}`
- `POST /api/admin/companies`, `PUT /api/admin/companies/{companyId}`, `DELETE /api/admin/companies/{companyId}`
- `POST /api/admin/job-postings`, `PUT /api/admin/job-postings/{jobPostingId}`,
  `DELETE /api/admin/job-postings/{jobPostingId}`
- 생성은 201·Location·상세 DTO, 조회·수정은 200, 삭제·관심 설정·해제는 204로 응답한다.
- 기업별 목록과 전체 공고의 companyId 필터 모두 미존재 기업은 404, 존재하는 기업의 빈 목록은 200으로 처리한다.
- 신규 오류: `COMPANY_NOT_FOUND`·`JOB_POSTING_NOT_FOUND` 404, `COMPANY_HAS_JOB_POSTINGS` 409, `FORBIDDEN` 403. 기존
  `USER_NOT_FOUND`, `INVALID_ACCESS_TOKEN`, `VALIDATION_ERROR`를 재사용한다.
- JSON·enum·숫자·시각 변환 및 날짜 구간 오류도 GlobalExceptionHandler와 ErrorResponse 형식으로 처리한다.

### 검증 상태와 재개 순서

- 2026-09-07 재개 지점: 전체 307개 테스트 성공·건너뜀 0을 확인해 기업·채용공고 단계를 완료했다. 다음 작업은 RAG 문서 모델·metadata·접근 제어 설계다. 아래 항목은 2026-09-04 당시 검증 상태와 구현 순서의 이력이며, 최신 결과는 [테스트 실행 기록](TEST_RESULTS.md)을 따른다.

- 2026-09-04: 실제 파일과 Git 상태를 확인함. 기업·채용공고 테스트는 아직 작성되지 않았으며 컴파일·테스트·DB migration 실행 결과는 확인되지 않음.
- 테스트 실행 명령·날짜·성공 여부: 미실행으로 해당 없음. 기존 전체 205개 성공 기록은 이전 이력서 단계의 결과이며 이번 변경의 검증 결과가 아니다.
- 2026-09-04 공통 기반 추가 확인: 신규 파일 4개의 코드를 확인했으며 관련 테스트는 미작성·미실행이다. 컴파일 결과도 미확인이다. 기존 테스트 성공 기록을 이번 변경의 검증으로 간주하지 않는다.
- 다음 작업은 4-1단계의 나머지인 기업·채용공고 요청·응답 DTO 작성이다. 이후 4-2 Service·업무 예외, 5단계 Controller·전역 예외 연결 순서로 진행한다.
- 6단계에서 반영 코드를 확인한 뒤 Codex가 정상·경계·실패 테스트를 작성한다. 사용자는 `.\gradlew.bat test`로 실행하고 결과를 전달한다. Docker 기반 MySQL 통합 테스트의 skip
  여부도 확인한다.
- 검증 대상: 문자열·URL·페이지 경계, 시작·종료 시각 경계와 UTC 변환, 검색 escape·projection·페이징, DB 역할 변경·탈퇴·비인증, FK·unique·CHECK·cascade, 관심 중복
  등록과 기업 삭제·공고 생성 경합.
- 구현 코드는 채팅에서 하위 단계별로 제공하고 사용자가 반영한다. 새 파일은 전체 코드, 기존 파일은 변경 위치와 필요한 문맥을 제공한다.
- `COMPANY_JOB_POSTING_DESIGN.md`, `COMPANY_JOB_POSTING_IMPLEMENTATION.md`는 임시 설계·코드 가이드다. 지속적으로 필요한 결정과 재개 지점은 이 문서에
  통합했으므로 두 파일 없이 개발을 이어갈 수 있다. 미반영 가이드 코드는 구현 완료의 근거로 사용하지 않는다.
