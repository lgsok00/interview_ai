package com.interviewai.interview.service;

import com.interviewai.company.entity.Company;
import com.interviewai.coverletter.entity.CoverLetter;
import com.interviewai.coverletter.entity.CoverLetterRepresentative;
import com.interviewai.coverletter.entity.CoverLetterVersion;
import com.interviewai.coverletter.exception.CoverLetterVersionNotFoundException;
import com.interviewai.coverletter.repository.CoverLetterRepresentativeRepository;
import com.interviewai.coverletter.repository.CoverLetterVersionRepository;
import com.interviewai.interview.exception.RepresentativeResumeNotReadyException;
import com.interviewai.jobposting.entity.JobPosting;
import com.interviewai.jobposting.exception.JobPostingNotFoundException;
import com.interviewai.jobposting.repository.JobPostingRepository;
import com.interviewai.resume.entity.Resume;
import com.interviewai.resume.entity.ResumeRepresentative;
import com.interviewai.resume.enums.ResumeExtractionStatus;
import com.interviewai.resume.repository.ResumeRepresentativeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterviewSessionSnapshotAssemblerTest {

    private static final Long USER_ID = 1L;
    private static final Long JOB_POSTING_ID = 10L;

    @Mock
    JobPostingRepository jobPostingRepository;
    @Mock
    CoverLetterRepresentativeRepository coverLetterRepresentativeRepository;
    @Mock
    CoverLetterVersionRepository coverLetterVersionRepository;
    @Mock
    ResumeRepresentativeRepository resumeRepresentativeRepository;
    @Mock
    JobPosting jobPosting;
    @Mock
    Company company;
    @Mock
    CoverLetterRepresentative coverLetterRepresentative;
    @Mock
    CoverLetter coverLetter;
    @Mock
    CoverLetterVersion coverLetterVersion;
    @Mock
    ResumeRepresentative resumeRepresentative;
    @Mock
    Resume resume;

    private InterviewSessionSnapshotAssembler assembler;


    @BeforeEach
    void setUp() {
        assembler = new InterviewSessionSnapshotAssembler(
                jobPostingRepository,
                coverLetterRepresentativeRepository,
                coverLetterVersionRepository,
                resumeRepresentativeRepository
        );
    }


    @Test
    @DisplayName("채용공고와 대표 자기소개서 현재 버전 및 대표 이력서를 스냅샷으로 조립한다")
    void assemblesAllRepresentativeSnapshots() {
        stubJobPosting();
        stubCoverLetter();
        stubCompletedResume();

        InterviewSourceSnapshot snapshot = assembler.assemble(USER_ID, JOB_POSTING_ID);

        assertThat(snapshot.jobPosting()).isEqualTo(new InterviewSourceSnapshot.JobPostingSnapshot(
                JOB_POSTING_ID, "인터뷰AI", "백엔드 개발자", "Backend", "채용공고 본문"
        ));
        assertThat(snapshot.coverLetter()).isEqualTo(new InterviewSourceSnapshot.PersonalDocumentSnapshot(
                20L, "현재 자기소개서", "현재 자기소개서 본문"
        ));
        assertThat(snapshot.resume()).isEqualTo(new InterviewSourceSnapshot.PersonalDocumentSnapshot(
                30L, "대표 이력서", "추출된 이력서 본문"
        ));
        verify(coverLetterVersionRepository)
                .findByCoverLetter_IdAndVersionNumber(20L, 3);
    }


    @Test
    @DisplayName("대표 문서가 설정되지 않았으면 채용공고 스냅샷만 조립한다")
    void omitsUnsetRepresentativeDocuments() {
        stubJobPosting();
        when(coverLetterRepresentativeRepository.findDetailByUserId(USER_ID)).thenReturn(Optional.empty());
        when(resumeRepresentativeRepository.findDetailByUserId(USER_ID)).thenReturn(Optional.empty());

        InterviewSourceSnapshot snapshot = assembler.assemble(USER_ID, JOB_POSTING_ID);

        assertThat(snapshot.coverLetter()).isNull();
        assertThat(snapshot.resume()).isNull();
        verifyNoInteractions(coverLetterVersionRepository);
    }


    @Test
    @DisplayName("존재하지 않는 채용공고이면 대표 문서를 조회하지 않는다")
    void rejectsUnknownJobPostingBeforeReadingRepresentatives() {
        when(jobPostingRepository.findDetail(JOB_POSTING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assembler.assemble(USER_ID, JOB_POSTING_ID))
                .isInstanceOf(JobPostingNotFoundException.class);

        verifyNoInteractions(
                coverLetterRepresentativeRepository,
                coverLetterVersionRepository,
                resumeRepresentativeRepository
        );
    }


    @Test
    @DisplayName("대표 자기소개서의 현재 버전이 없으면 스냅샷 조립을 거부한다")
    void rejectsMissingCurrentCoverLetterVersion() {
        stubJobPosting();
        when(coverLetterRepresentativeRepository.findDetailByUserId(USER_ID))
                .thenReturn(Optional.of(coverLetterRepresentative));
        when(coverLetterRepresentative.getCoverLetter()).thenReturn(coverLetter);
        when(coverLetter.getId()).thenReturn(20L);
        when(coverLetter.getCurrentVersionNumber()).thenReturn(3);
        when(coverLetterVersionRepository.findByCoverLetter_IdAndVersionNumber(20L, 3))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> assembler.assemble(USER_ID, JOB_POSTING_ID))
                .isInstanceOf(CoverLetterVersionNotFoundException.class);

        verifyNoInteractions(resumeRepresentativeRepository);
    }


    @Test
    @DisplayName("텍스트 추출이 완료되지 않은 대표 이력서는 사용하지 않는다")
    void rejectsRepresentativeResumeWhoseExtractionIsPending() {
        stubJobPosting();
        when(coverLetterRepresentativeRepository.findDetailByUserId(USER_ID)).thenReturn(Optional.empty());
        when(resumeRepresentativeRepository.findDetailByUserId(USER_ID))
                .thenReturn(Optional.of(resumeRepresentative));
        when(resumeRepresentative.getResume()).thenReturn(resume);
        when(resume.getExtractionStatus()).thenReturn(ResumeExtractionStatus.PENDING);

        assertThatThrownBy(() -> assembler.assemble(USER_ID, JOB_POSTING_ID))
                .isInstanceOf(RepresentativeResumeNotReadyException.class)
                .hasMessage("대표 이력서의 텍스트 추출이 완료되지 않았습니다.");
    }


    @Test
    @DisplayName("추출 완료 상태라도 대표 이력서 본문이 비어 있으면 사용하지 않는다")
    void rejectsCompletedRepresentativeResumeWithBlankText() {
        stubJobPosting();
        when(coverLetterRepresentativeRepository.findDetailByUserId(USER_ID)).thenReturn(Optional.empty());
        when(resumeRepresentativeRepository.findDetailByUserId(USER_ID))
                .thenReturn(Optional.of(resumeRepresentative));
        when(resumeRepresentative.getResume()).thenReturn(resume);
        when(resume.getExtractionStatus()).thenReturn(ResumeExtractionStatus.COMPLETED);
        when(resume.getExtractedText()).thenReturn(" ");

        assertThatThrownBy(() -> assembler.assemble(USER_ID, JOB_POSTING_ID))
                .isInstanceOf(RepresentativeResumeNotReadyException.class);
    }


    private void stubJobPosting() {
        when(jobPostingRepository.findDetail(JOB_POSTING_ID)).thenReturn(Optional.of(jobPosting));
        when(jobPosting.getId()).thenReturn(JOB_POSTING_ID);
        when(jobPosting.getCompany()).thenReturn(company);
        when(company.getName()).thenReturn("인터뷰AI");
        when(jobPosting.getTitle()).thenReturn("백엔드 개발자");
        when(jobPosting.getJobRole()).thenReturn("Backend");
        when(jobPosting.getDescription()).thenReturn("채용공고 본문");
    }


    private void stubCoverLetter() {
        when(coverLetterRepresentativeRepository.findDetailByUserId(USER_ID))
                .thenReturn(Optional.of(coverLetterRepresentative));
        when(coverLetterRepresentative.getCoverLetter()).thenReturn(coverLetter);
        when(coverLetter.getId()).thenReturn(20L);
        when(coverLetter.getCurrentVersionNumber()).thenReturn(3);
        when(coverLetterVersionRepository.findByCoverLetter_IdAndVersionNumber(20L, 3))
                .thenReturn(Optional.of(coverLetterVersion));
        when(coverLetterVersion.getTitle()).thenReturn("현재 자기소개서");
        when(coverLetterVersion.getContent()).thenReturn("현재 자기소개서 본문");
    }


    private void stubCompletedResume() {
        when(resumeRepresentativeRepository.findDetailByUserId(USER_ID))
                .thenReturn(Optional.of(resumeRepresentative));
        when(resumeRepresentative.getResume()).thenReturn(resume);
        when(resume.getExtractionStatus()).thenReturn(ResumeExtractionStatus.COMPLETED);
        when(resume.getExtractedText()).thenReturn("추출된 이력서 본문");
        when(resume.getId()).thenReturn(30L);
        when(resume.getTitle()).thenReturn("대표 이력서");
    }
}
