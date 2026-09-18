# 외부 수집·AI 문서 초안 계약

[프로젝트 현황으로 돌아가기](../PROJECT_STATUS.md)

2026-09-17에 확정한 계약이다. 2026-09-18 V18/V19로 외부 수집 요청·불변 스냅샷의 영속 기반만 구현·검증했으며, 실제 네트워크 수집·사이트 파서·관리자 승인 API는 보류했다. AI 문서
초안은 아직 구현하지 않았다. 외부 수집은 공용 기업·채용공고의 **관리자 검수 후보**를 만들고, AI 문서 초안은 인증 사용자의 **자기소개서 검수본**을 만든다. 둘 다 원본 데이터를 자동으로 바꾸지 않는다.

## 범위와 비범위

- 외부 수집의 첫 대상은 관리자가 요청한 기업 홈페이지 또는 채용공고의 단일 HTTPS URL이다. 사이트 전체 탐색, 로그인·우회·CAPTCHA 처리, 무단 반복 수집, 제3자 사이트의 영구 원문 공개는 범위
  밖이다.
- 수집 결과는 기업 또는 채용공고의 생성·수정 후보일 뿐이다. 승인 전에는 `companies`, `job_postings`, RAG 원본·색인 작업을 만들거나 바꾸지 않는다.
- AI 초안의 첫 대상은 사용자가 소유한 기존 자기소개서다. 이력서·PDF 생성, 자기소개서 신규 문서 자동 생성, 대표 문서 자동 변경, 면접 결과의 자동 반영은 범위 밖이다.
- AI 결과는 참고용 초안이다. 생성 성공을 사실성·합격 가능성·저작권 적법성의 보증으로 표현하지 않으며, 생성만으로 자기소개서 버전이나 RAG를 변경하지 않는다.

## 외부 수집 계약

### 요청·입력·보안

- `POST /api/admin/external-collections`는 활성 관리자만 호출한다. 요청에는 `kind`(`COMPANY` 또는 `JOB_POSTING`)와 `sourceUrl`을 받는다. URL은
  절대 HTTPS URL, 사용자 정보가 없는 host 포함 주소, 최대 2,048자로 정규화한다.
- 실제 HTTP 연결 전과 redirect마다 scheme·host·port·DNS 결과를 검증한다. loopback, link-local, private, multicast, unspecified IP와 비표준
  포트는 거부한다. redirect는 최대 3회, 요청 timeout은 10초, 응답은 HTML/XHTML만 허용하고 압축 해제 후 최대 2 MiB에서 중단한다.
- 수집 어댑터는 allowlist된 공급자/도메인으로 시작한다. robots.txt·서비스 약관·rate limit은 공급자 등록 때 검토하고, 사용자 제공 URL이라는 이유만으로 접근 제한을 우회하지 않는다.
  요청 헤더에는 인증정보·사용자 쿠키를 보내지 않는다.
- 수집 URL과 최종 URL은 운영 조회에서만 보이며, 응답 본문·HTML·인증 토큰·쿠키·IP 주소는 API나 로그에 노출하지 않는다. 실패 코드는 정규화해 저장한다.

### 불변 원본과 상태

- `external_collection_requests`는 요청 단위의 `id`, `kind`, 요청 URL/정규화 URL, 요청 관리자 ID, 상태, 현재 시도 횟수, 마지막 실패 코드와 UTC 마이크로초 시각을
  가진다.
- 상태는 `PENDING → RUNNING → REVIEW_READY` 또는 `FAILED`이며, `REVIEW_READY → APPROVED` 또는 `REJECTED`로 검수를 종료한다. `FAILED`는 명시
  재시도 시에만 새 시도와 함께 `PENDING`으로 돌아가며, `APPROVED`와 `REJECTED`는 종료 상태다. 재시도는 이전 원본 성공 스냅샷을 덮어쓰지 않는다.
- 시도마다 `external_collection_snapshots`에 요청 ID, attempt UUID, 수집 시각, 최종 URL, HTTP 상태, content type, 본문 SHA-256, parser
  버전, 정규화한 추출 텍스트와 구조화 후보 JSON을 불변 저장한다. 텍스트는 최대 100,000자이며 초과·파싱 불가는 `FAILED`로 처리한다.
- 후보 JSON은 기업이면 `name`, `industry`, `description`, `websiteUrl`, `location`, 공고면 `companyName`, `title`, `jobRole`,
  `employmentType`, `location`, `description`, `sourceUrl`, `opensAt`, `closesAt`만 허용한다. 값 누락은 null이며 추정값을 채우지 않는다. 원문
  인용 위치/문장 범위도 후보별 근거로 보존한다.

### 검수·반영

- `GET /api/admin/external-collections`와 `GET /api/admin/external-collections/{id}`는 상태·요청/수집 시각·정규화 후보·근거·실패 코드만 반환한다.
  원문 전체는 반환하지 않는다.
- `POST /api/admin/external-collections/{id}/approve`는 종료 전 `REVIEW_READY`인 정확한 스냅샷 ID와, 관리자가 확정·수정한 기업/공고 DTO를 함께 받는다.
  후보 JSON은 입력 편의이며 승인 DTO는 기존 `CatalogInput`의 필수값·길이·URL·기간 검증을 다시 통과해야 한다.
- 승인 트랜잭션은 요청 행을 잠근 뒤 한 번만 수행한다. 새 기업·공고를 만들거나 명시한 기존 ID를 전체 교체하고, 기존 `CompanyService`/`JobPostingService`의 RAG UPSERT
  경로를 사용한다. 성공하면 `APPROVED`로 종료한다. 같은 승인 재전송은 이미 반영된 결과를 반환하고 다른 스냅샷/내용은 409로 거부한다.
- 수집 결과만으로 기존 기업·공고를 찾아 자동 병합하거나 삭제하지 않는다. 중복 판정과 변경 영향은 관리자가 승인 단계에서 결정한다.

## AI 자기소개서 초안 계약

### 입력 스냅샷

- `POST /api/cover-letters/{coverLetterId}/drafts`는 소유자만 호출한다. URL의 자기소개서는 존재해야 하며, 요청에는 필수 `jobPostingId`, 선택
  `instruction`(trim 후 1~1,000자), 선택 `resumeId`를 받는다.
- 공고와 기업은 요청 시점의 현재 상세값을 스냅샷한다. `resumeId`를 생략하면 대표 이력서를 사용하며, 선택된 이력서는 소유자이고 `COMPLETED`이며 빈 추출 본문이 아니어야 한다. 대표 이력서가
  없거나 준비되지 않으면 409 `REPRESENTATIVE_RESUME_NOT_READY`를 재사용한다.
- 대상 자기소개서는 현재 버전의 제목·본문·버전 번호를 스냅샷한다. 초안은 이 입력 스냅샷, 입력 해시, prompt template 버전, 모델 이름, 생성 시각을 보관한다. 이후 원본 수정·삭제나 공고 변경이
  과거 초안의 입력을 바꾸지 않는다.
- 사용자 지시·이력서·자기소개서·외부 수집 텍스트는 모두 신뢰할 수 없는 데이터로 구분자 안에 넣는다. 시스템 지시를 무시하게 하는 내용, 외부 도구 호출, 사실을 꾸며내라는 요구는 모델 명령으로 취급하지 않는다.

### 생성·검수·재생성

- 초안은 비동기 작업이다. 생성 요청은 202와 draft ID를 반환하고 상태는 `PENDING → RUNNING → REVIEW_READY` 또는 `FAILED`다. 120초 lease, 최대 3회 자동 시도,
  만료 작업 회수, attempt UUID와 늦은 완료 차단은 기존 면접 생성 작업과 같은 실행 원칙을 따른다.
- AI가 비활성·quota 소진·timeout·네트워크 오류·형식 오류인 경우 고정 문구 fallback을 만들지 않는다. 초안은 `FAILED`와 정규화 실패 코드로 남으며 사용자가 재요청할 수 있다.
- 모델 응답은 JSON의 `title`, `content`, `changeSummary`, `warnings`만 허용한다. 제목은 trim 후 1~100자, 본문은 trim 후 1~20,000자, 요약·경고는 각
  최대 1,000자로 검증한다. 검증 실패는 `AI_INVALID_OUTPUT` 실패다.
- `POST /api/cover-letters/{coverLetterId}/drafts/{draftId}/regenerate`는 기존 결과를 덮어쓰지 않고 새 draft ID를 만든다. 새 작업은 최신 현재 버전을
  다시 스냅샷하므로, 이전 초안과 입력이 다를 수 있다. 소유자 외에는 초안 존재를 404로 숨긴다.

### 명시적 적용

- `GET /api/cover-letters/{coverLetterId}/drafts` 및 상세 조회는 소유자에게만 입력 기준 버전, 상태, 결과와 경고를 제공한다. prompt 원문·모델의 내부
  reasoning·다른 사용자의 데이터는 반환하지 않는다.
- `POST /api/cover-letters/{coverLetterId}/drafts/{draftId}/apply`는 `REVIEW_READY`인 초안만 적용한다. 적용 시 대상 자기소개서 행을 잠그고 초안의
  기준 버전이 현재 버전과 같은지 확인한다. 다르면 409 `DRAFT_BASE_VERSION_CONFLICT`이며 사용자가 재생성하거나 직접 편집해야 한다.
- 기준 버전이 같으면 검수자가 제출한 `title`/`content`를 다시 1~100/1~20,000자로 검증해 새 `CoverLetterVersion`으로 저장한다. 이 경로는 기존 수정과 동일하게 제목·현재
  버전·RAG UPSERT를 한 트랜잭션으로 변경한다. 적용된 draft는 `APPLIED`로 종료하며 같은 적용 재전송은 그 결과를 반환한다.
- 검수자는 AI 결과를 수정해 적용할 수 있다. 따라서 AI 결과와 실제 적용 버전은 다를 수 있으며, AI 생성 내용은 사용자 명시 적용 전 대표 문서나 면접 입력에 포함되지 않는다.

## 구현 순서와 검증 기준

1. V18/V19로 수집 요청·불변 스냅샷·상태/lease·수동 재시도와 최대 9회 이력 제약을 추가하고 검증했다.
2. 관리자 요청·조회·재시도·거절과 실제 수집·단일 승인 반영은 허가된 데이터 소스가 정해질 때 구현한다. 승인 시 기존 카탈로그 쓰기와 RAG 등록을 재사용한다.
3. V20으로 사용자 소유 draft·입력 스냅샷·실행 작업을 추가하고, Chat 어댑터·worker·조회·재생성·적용을 구현한다.
4. 정상: 수집 후보 승인 후 공용 데이터/RAG 반영, AI 초안 생성·검수 수정 적용. 경계: 빈 선택값, 최대 길이, redirect, 만료 lease, 재전송, 기준 버전 일치. 실패: SSRF 대상,
   비HTML/초과 응답, parser/AI 오류, 타인 접근, 상태 충돌, 기준 버전 불일치, 승인/적용 rollback과 동시 승인·적용을 검증한다.

테스트는 Codex가 구현 반영 후 작성하며, 사용자가 Windows Gradle Wrapper로 실행한 결과를 확인하기 전에는 구현 완료로 기록하지 않는다.
