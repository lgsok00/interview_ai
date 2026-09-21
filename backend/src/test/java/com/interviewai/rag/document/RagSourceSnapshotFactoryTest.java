package com.interviewai.rag.document;

import com.interviewai.company.entity.Company;
import com.interviewai.coverletter.entity.CoverLetter;
import com.interviewai.coverletter.entity.CoverLetterVersion;
import com.interviewai.jobposting.entity.JobPosting;
import com.interviewai.jobposting.enums.EmploymentType;
import com.interviewai.resume.entity.Resume;
import com.interviewai.resume.enums.ResumeExtractionStatus;
import com.interviewai.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagSourceSnapshotFactoryTest {

    @Mock private Company company;
    @Mock private JobPosting jobPosting;
    @Mock private CoverLetter coverLetter;
    @Mock private CoverLetterVersion currentVersion;
    @Mock private Resume resume;
    @Mock private User user;

    private RagSourceSnapshotFactory factory;


    @BeforeEach
    void setUp() {
        factory = new RagSourceSnapshotFactory();
    }


    @Test
    @DisplayName("기업 정보를 공용 RAG 문서로 변환한다")
    void createsCompanySnapshot() {
        when(company.getId()).thenReturn(10L);
        when(company.getName()).thenReturn(" 인터뷰 AI ");
        when(company.getIndustry()).thenReturn("IT");
        when(company.getLocation()).thenReturn(null);
        when(company.getDescription()).thenReturn(" 면접 지원 서비스 ");

        RagSourceSnapshot snapshot = factory.fromCompany(company);

        assertThat(snapshot.sourceKey()).isEqualTo(new RagSourceKey(RagSourceType.COMPANY, 10L));
        assertThat(snapshot.visibility()).isEqualTo(RagVisibility.AUTHENTICATED_SHARED);
        assertThat(snapshot.ownerUserId()).isNull();
        assertThat(snapshot.companyId()).isEqualTo(10L);
        assertThat(snapshot.title()).isEqualTo("인터뷰 AI");
        assertThat(snapshot.content()).isEqualTo("""
                기업명: 인터뷰 AI
                산업: IT
                기업 설명:
                면접 지원 서비스""");
        assertThat(snapshot.sourceRevision()).hasSize(64).matches("[0-9a-f]{64}");
    }


    @Test
    @DisplayName("동일한 기업 내용은 같은 revision을 생성하고 검색 내용이 바뀌면 revision도 바뀐다")
    void createsStableRevisionFromIndexedContent() {
        when(company.getId()).thenReturn(10L);
        when(company.getName()).thenReturn("인터뷰 AI");
        when(company.getDescription())
                .thenReturn("기존 설명")
                .thenReturn("기존 설명")
                .thenReturn("변경된 설명");

        RagSourceSnapshot first = factory.fromCompany(company);
        RagSourceSnapshot second = factory.fromCompany(company);
        RagSourceSnapshot changed = factory.fromCompany(company);

        assertThat(second.sourceRevision()).isEqualTo(first.sourceRevision());
        assertThat(changed.sourceRevision()).isNotEqualTo(first.sourceRevision());
    }


    @Test
    @DisplayName("채용공고와 소속 기업 정보를 공용 RAG 문서로 변환한다")
    void createsJobPostingSnapshot() {
        when(jobPosting.getId()).thenReturn(20L);
        when(jobPosting.getCompany()).thenReturn(company);
        when(jobPosting.getTitle()).thenReturn("백엔드 개발자");
        when(jobPosting.getJobRole()).thenReturn("서버 개발");
        when(jobPosting.getEmploymentType()).thenReturn(EmploymentType.FULL_TIME);
        when(jobPosting.getLocation()).thenReturn("서울");
        when(jobPosting.getDescription()).thenReturn("Java와 Spring 경험");
        when(company.getId()).thenReturn(10L);
        when(company.getName()).thenReturn("인터뷰 AI");

        RagSourceSnapshot snapshot = factory.fromJobPosting(jobPosting);

        assertThat(snapshot.sourceKey()).isEqualTo(new RagSourceKey(RagSourceType.JOB_POSTING, 20L));
        assertThat(snapshot.companyId()).isEqualTo(10L);
        assertThat(snapshot.content()).contains(
                "기업명: 인터뷰 AI",
                "채용 제목: 백엔드 개발자",
                "직무: 서버 개발",
                "고용 형태: FULL_TIME",
                "위치: 서울",
                "채용 내용:\nJava와 Spring 경험"
        );
    }


    @Test
    @DisplayName("자기소개서 현재 버전을 개인 RAG 문서로 변환한다")
    void createsCurrentCoverLetterSnapshot() {
        stubCurrentCoverLetter();

        RagSourceSnapshot snapshot = factory.fromCoverLetter(coverLetter, currentVersion);

        assertThat(snapshot.sourceKey()).isEqualTo(new RagSourceKey(RagSourceType.COVER_LETTER, 30L));
        assertThat(snapshot.visibility()).isEqualTo(RagVisibility.PRIVATE);
        assertThat(snapshot.ownerUserId()).isEqualTo(1L);
        assertThat(snapshot.companyId()).isNull();
        assertThat(snapshot.title()).isEqualTo("지원 동기");
        assertThat(snapshot.content()).isEqualTo("""
                자기소개서 제목: 지원 동기
                버전: 1
                자기소개서 본문:
                프로젝트 경험""");
    }


    @Test
    @DisplayName("다른 자기소개서의 버전은 변환하지 않는다")
    void rejectsVersionFromAnotherCoverLetter() {
        CoverLetter anotherCoverLetter = org.mockito.Mockito.mock(CoverLetter.class);
        when(coverLetter.getId()).thenReturn(30L);
        when(currentVersion.getCoverLetter()).thenReturn(anotherCoverLetter);
        when(anotherCoverLetter.getId()).thenReturn(31L);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> factory.fromCoverLetter(coverLetter, currentVersion))
                .withMessage("자기소개서와 버전의 문서 ID가 일치해야 합니다.");
    }


    @Test
    @DisplayName("현재 버전이 아닌 자기소개서 버전은 변환하지 않는다")
    void rejectsNonCurrentCoverLetterVersion() {
        when(coverLetter.getId()).thenReturn(30L);
        when(coverLetter.getCurrentVersionNumber()).thenReturn(2);
        when(currentVersion.getCoverLetter()).thenReturn(coverLetter);
        when(currentVersion.getVersionNumber()).thenReturn(1);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> factory.fromCoverLetter(coverLetter, currentVersion))
                .withMessage("현재 자기소개서 버전만 RAG 문서로 변환할 수 있습니다.");
    }


    @Test
    @DisplayName("텍스트 추출이 완료된 이력서를 개인 RAG 문서로 변환한다")
    void createsCompletedResumeSnapshot() {
        stubResume(ResumeExtractionStatus.COMPLETED, " 프로젝트 경험 ");

        RagSourceResolution resolution = factory.fromResume(resume);

        assertThat(resolution.status()).isEqualTo(RagSourceStatus.READY);
        assertThat(resolution.isReady()).isTrue();
        assertThat(resolution.optionalSnapshot()).hasValueSatisfying(snapshot -> {
            assertThat(snapshot.ownerUserId()).isEqualTo(1L);
            assertThat(snapshot.title()).isEqualTo("백엔드 이력서");
            assertThat(snapshot.content()).isEqualTo("""
                    이력서 제목: 백엔드 이력서
                    이력서 본문:
                    프로젝트 경험""");
        });
    }


    @Test
    @DisplayName("대기 중인 이력서는 PENDING 상태로 반환한다")
    void returnsPendingResumeResolution() {
        stubResume(ResumeExtractionStatus.PENDING, null);

        RagSourceResolution resolution = factory.fromResume(resume);

        assertThat(resolution.status()).isEqualTo(RagSourceStatus.PENDING);
        assertThat(resolution.optionalSnapshot()).isEmpty();
    }


    @Test
    @DisplayName("텍스트 추출에 실패한 이력서는 EXTRACTION_FAILED 상태로 반환한다")
    void returnsFailedResumeResolution() {
        stubResume(ResumeExtractionStatus.FAILED, null);

        RagSourceResolution resolution = factory.fromResume(resume);

        assertThat(resolution.status()).isEqualTo(RagSourceStatus.EXTRACTION_FAILED);
        assertThat(resolution.optionalSnapshot()).isEmpty();
    }


    @Test
    @DisplayName("추출 완료 상태지만 본문이 비어 있는 이력서는 EMPTY_CONTENT 상태로 반환한다")
    void returnsEmptyResumeResolution() {
        stubResume(ResumeExtractionStatus.COMPLETED, " \n\t ");

        RagSourceResolution resolution = factory.fromResume(resume);

        assertThat(resolution.status()).isEqualTo(RagSourceStatus.EMPTY_CONTENT);
        assertThat(resolution.optionalSnapshot()).isEmpty();
    }


    private void stubCurrentCoverLetter() {
        when(coverLetter.getId()).thenReturn(30L);
        when(coverLetter.getCurrentVersionNumber()).thenReturn(1);
        when(coverLetter.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(1L);
        when(currentVersion.getCoverLetter()).thenReturn(coverLetter);
        when(currentVersion.getVersionNumber()).thenReturn(1);
        when(currentVersion.getTitle()).thenReturn("지원 동기");
        when(currentVersion.getContent()).thenReturn("프로젝트 경험");
    }


    private void stubResume(ResumeExtractionStatus status, String extractedText) {
        when(resume.getId()).thenReturn(40L);
        when(resume.getExtractionStatus()).thenReturn(status);

        if (status == ResumeExtractionStatus.COMPLETED) {
            when(resume.getExtractedText()).thenReturn(extractedText);
        }

        if (status == ResumeExtractionStatus.COMPLETED
                && extractedText != null
                && !extractedText.isBlank()) {
            when(resume.getTitle()).thenReturn("백엔드 이력서");
            when(resume.getUser()).thenReturn(user);
            when(user.getId()).thenReturn(1L);
        }
    }
}
