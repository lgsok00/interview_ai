package com.interviewai.rag.document;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class RagSourceDocumentTest {

    @Test
    @DisplayName("문서 유형별 공개 범위와 기업 소속 여부를 반환한다")
    void returnSourceTypePolicies() {
        assertThat(RagSourceType.COMPANY.visibility()).isEqualTo(RagVisibility.AUTHENTICATED_SHARED);
        assertThat(RagSourceType.JOB_POSTING.visibility()).isEqualTo(RagVisibility.AUTHENTICATED_SHARED);
        assertThat(RagSourceType.COVER_LETTER.visibility()).isEqualTo(RagVisibility.PRIVATE);
        assertThat(RagSourceType.RESUME.visibility()).isEqualTo(RagVisibility.PRIVATE);

        assertThat(RagSourceType.COMPANY.belongsToCompany()).isTrue();
        assertThat(RagSourceType.JOB_POSTING.belongsToCompany()).isTrue();
        assertThat(RagSourceType.COVER_LETTER.belongsToCompany()).isFalse();
        assertThat(RagSourceType.RESUME.belongsToCompany()).isFalse();
    }

    @Test
    @DisplayName("양수 sourceId로 문서 키를 생성한다")
    void createSourceKey() {
        RagSourceKey sourceKey = new RagSourceKey(RagSourceType.COMPANY, 1L);

        assertThat(sourceKey.sourceType()).isEqualTo(RagSourceType.COMPANY);
        assertThat(sourceKey.sourceId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("문서 키의 유형과 ID는 필수다")
    void rejectMissingSourceKeyValues() {
        assertThatNullPointerException()
                .isThrownBy(() -> new RagSourceKey(null, 1L))
                .withMessage("sourceType은 필수입니다.");

        assertThatNullPointerException()
                .isThrownBy(() -> new RagSourceKey(RagSourceType.COMPANY, null))
                .withMessage("sourceId는 필수입니다.");
    }

    @Test
    @DisplayName("문서 키의 ID가 0 이하이면 거부한다")
    void rejectNonPositiveSourceId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RagSourceKey(RagSourceType.COMPANY, 0L))
                .withMessage("sourceId는 양수여야 합니다.");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RagSourceKey(RagSourceType.COMPANY, -1L))
                .withMessage("sourceId는 양수여야 합니다.");
    }

    @Test
    @DisplayName("기업 문서는 공용 범위와 자신의 기업 ID를 사용한다")
    void createCompanySnapshot() {
        RagSourceSnapshot snapshot = new RagSourceSnapshot(
                new RagSourceKey(RagSourceType.COMPANY, 10L),
                null,
                10L,
                "  인터뷰 AI  ",
                "  백엔드 개발 기업  ",
                "  revision-1  "
        );

        assertThat(snapshot.sourceType()).isEqualTo(RagSourceType.COMPANY);
        assertThat(snapshot.sourceId()).isEqualTo(10L);
        assertThat(snapshot.visibility()).isEqualTo(RagVisibility.AUTHENTICATED_SHARED);
        assertThat(snapshot.ownerUserId()).isNull();
        assertThat(snapshot.companyId()).isEqualTo(10L);
        assertThat(snapshot.title()).isEqualTo("인터뷰 AI");
        assertThat(snapshot.content()).isEqualTo("백엔드 개발 기업");
        assertThat(snapshot.sourceRevision()).isEqualTo("revision-1");
    }

    @Test
    @DisplayName("채용공고 문서는 공용 범위와 소속 기업 ID를 사용한다")
    void createJobPostingSnapshot() {
        RagSourceSnapshot snapshot = new RagSourceSnapshot(
                new RagSourceKey(RagSourceType.JOB_POSTING, 20L),
                null,
                10L,
                "백엔드 개발자",
                "Java와 Spring 경험",
                "revision-2"
        );

        assertThat(snapshot.visibility()).isEqualTo(RagVisibility.AUTHENTICATED_SHARED);
        assertThat(snapshot.ownerUserId()).isNull();
        assertThat(snapshot.companyId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("자기소개서와 이력서는 개인 범위와 소유자 ID를 사용한다")
    void createPrivateSnapshots() {
        RagSourceSnapshot coverLetter = new RagSourceSnapshot(
                new RagSourceKey(RagSourceType.COVER_LETTER, 30L),
                1L,
                null,
                "지원 동기",
                "지원 동기 본문",
                "revision-3"
        );
        RagSourceSnapshot resume = new RagSourceSnapshot(
                new RagSourceKey(RagSourceType.RESUME, 40L),
                1L,
                null,
                "백엔드 이력서",
                "프로젝트 경험",
                "revision-4"
        );

        assertThat(coverLetter.visibility()).isEqualTo(RagVisibility.PRIVATE);
        assertThat(coverLetter.ownerUserId()).isEqualTo(1L);
        assertThat(coverLetter.companyId()).isNull();
        assertThat(resume.visibility()).isEqualTo(RagVisibility.PRIVATE);
        assertThat(resume.ownerUserId()).isEqualTo(1L);
        assertThat(resume.companyId()).isNull();
    }

    @Test
    @DisplayName("공용 문서에 소유자 ID가 있으면 거부한다")
    void rejectOwnerForSharedSnapshot() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RagSourceSnapshot(
                        new RagSourceKey(RagSourceType.COMPANY, 10L),
                        1L,
                        10L,
                        "기업",
                        "기업 설명",
                        "revision"
                ))
                .withMessage("공용 문서에는 ownerUserId를 지정할 수 없습니다.");
    }

    @Test
    @DisplayName("개인 문서에 유효한 소유자 ID가 없으면 거부한다")
    void rejectMissingOwnerForPrivateSnapshot() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> privateSnapshot(null))
                .withMessage("개인 문서에는 유효한 ownerUserId가 필요합니다.");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> privateSnapshot(0L))
                .withMessage("개인 문서에는 유효한 ownerUserId가 필요합니다.");
    }

    @Test
    @DisplayName("기업 문서의 companyId가 sourceId와 다르면 거부한다")
    void rejectMismatchedCompanyId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RagSourceSnapshot(
                        new RagSourceKey(RagSourceType.COMPANY, 10L),
                        null,
                        11L,
                        "기업",
                        "기업 설명",
                        "revision"
                ))
                .withMessage("기업 문서의 companyId는 sourceId와 같아야 합니다.");
    }

    @Test
    @DisplayName("채용공고 문서에 유효한 기업 ID가 없으면 거부한다")
    void rejectMissingCompanyIdForJobPosting() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> jobPostingSnapshot(null))
                .withMessage("채용공고 문서에는 유효한 companyId가 필요합니다.");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> jobPostingSnapshot(0L))
                .withMessage("채용공고 문서에는 유효한 companyId가 필요합니다.");
    }

    @Test
    @DisplayName("개인 문서에 기업 ID가 있으면 거부한다")
    void rejectCompanyIdForPrivateSnapshot() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RagSourceSnapshot(
                        new RagSourceKey(RagSourceType.RESUME, 40L),
                        1L,
                        10L,
                        "이력서",
                        "프로젝트 경험",
                        "revision"
                ))
                .withMessage("개인 문서에는 companyId를 지정할 수 없습니다.");
    }

    @Test
    @DisplayName("제목과 본문과 revision은 공백일 수 없다")
    void rejectBlankTextValues() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> companySnapshot(" ", "본문", "revision"))
                .withMessage("title은 비어 있을 수 없습니다.");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> companySnapshot("기업", "\n\t", "revision"))
                .withMessage("content은 비어 있을 수 없습니다.");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> companySnapshot("기업", "본문", ""))
                .withMessage("sourceRevision은 비어 있을 수 없습니다.");
    }

    @Test
    @DisplayName("스냅샷의 문서 키는 필수다")
    void rejectMissingSourceKey() {
        assertThatNullPointerException()
                .isThrownBy(() -> new RagSourceSnapshot(
                        null,
                        null,
                        null,
                        "제목",
                        "본문",
                        "revision"
                ))
                .withMessage("sourceKey는 필수입니다.");
    }

    private void companySnapshot(String title, String content, String sourceRevision) {
        new RagSourceSnapshot(
                new RagSourceKey(RagSourceType.COMPANY, 10L),
                null,
                10L,
                title,
                content,
                sourceRevision
        );
    }

    private void jobPostingSnapshot(Long companyId) {
        new RagSourceSnapshot(
                new RagSourceKey(RagSourceType.JOB_POSTING, 20L),
                null,
                companyId,
                "채용공고",
                "채용공고 본문",
                "revision"
        );
    }

    private void privateSnapshot(Long ownerUserId) {
        new RagSourceSnapshot(
                new RagSourceKey(RagSourceType.COVER_LETTER, 30L),
                ownerUserId,
                null,
                "자기소개서",
                "자기소개서 본문",
                "revision"
        );
    }
}
