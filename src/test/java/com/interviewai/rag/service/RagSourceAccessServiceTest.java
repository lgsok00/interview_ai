package com.interviewai.rag.service;

import com.interviewai.company.entity.Company;
import com.interviewai.company.exception.CompanyNotFoundException;
import com.interviewai.company.repository.CompanyRepository;
import com.interviewai.coverletter.entity.CoverLetter;
import com.interviewai.coverletter.entity.CoverLetterVersion;
import com.interviewai.coverletter.exception.CoverLetterNotFoundException;
import com.interviewai.coverletter.exception.CoverLetterVersionNotFoundException;
import com.interviewai.coverletter.repository.CoverLetterRepository;
import com.interviewai.coverletter.repository.CoverLetterVersionRepository;
import com.interviewai.global.security.AdminAuthorizationService;
import com.interviewai.jobposting.entity.JobPosting;
import com.interviewai.jobposting.exception.JobPostingNotFoundException;
import com.interviewai.jobposting.repository.JobPostingRepository;
import com.interviewai.rag.document.RagSourceResolution;
import com.interviewai.rag.document.RagSourceSnapshot;
import com.interviewai.rag.document.RagSourceSnapshotFactory;
import com.interviewai.resume.entity.Resume;
import com.interviewai.resume.exception.ResumeNotFoundException;
import com.interviewai.resume.repository.ResumeRepository;
import com.interviewai.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagSourceAccessServiceTest {

    private static final String SUBJECT = "1";
    private static final Long USER_ID = 1L;

    @Mock private AdminAuthorizationService authorizationService;
    @Mock private CompanyRepository companyRepository;
    @Mock private JobPostingRepository jobPostingRepository;
    @Mock private CoverLetterRepository coverLetterRepository;
    @Mock private CoverLetterVersionRepository versionRepository;
    @Mock private ResumeRepository resumeRepository;
    @Mock private RagSourceSnapshotFactory snapshotFactory;
    @Mock private User user;
    @Mock private Company company;
    @Mock private JobPosting jobPosting;
    @Mock private CoverLetter coverLetter;
    @Mock private CoverLetterVersion version;
    @Mock private Resume resume;
    @Mock private RagSourceSnapshot snapshot;
    @Mock private RagSourceResolution resolution;

    private RagSourceAccessService service;


    @BeforeEach
    void setUp() {
        service = new RagSourceAccessService(
                authorizationService,
                companyRepository,
                jobPostingRepository,
                coverLetterRepository,
                versionRepository,
                resumeRepository,
                snapshotFactory
        );
    }


    @Test
    @DisplayName("인증된 사용자가 기업 RAG 문서를 조회한다")
    void getsCompanySnapshot() {
        when(authorizationService.requireUser(SUBJECT)).thenReturn(user);
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
        when(snapshotFactory.fromCompany(company)).thenReturn(snapshot);

        assertThat(service.getCompany(SUBJECT, 10L)).isSameAs(snapshot);

        verify(authorizationService).requireUser(SUBJECT);
        verify(snapshotFactory).fromCompany(company);
    }


    @Test
    @DisplayName("존재하지 않는 기업은 RAG 문서로 조회할 수 없다")
    void rejectsUnknownCompany() {
        when(authorizationService.requireUser(SUBJECT)).thenReturn(user);
        when(companyRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCompany(SUBJECT, 10L))
                .isInstanceOf(CompanyNotFoundException.class);

        verifyNoInteractions(snapshotFactory);
    }


    @Test
    @DisplayName("인증된 사용자가 기업 정보가 포함된 채용공고 RAG 문서를 조회한다")
    void getsJobPostingSnapshot() {
        when(authorizationService.requireUser(SUBJECT)).thenReturn(user);
        when(jobPostingRepository.findDetail(20L)).thenReturn(Optional.of(jobPosting));
        when(snapshotFactory.fromJobPosting(jobPosting)).thenReturn(snapshot);

        assertThat(service.getJobPosting(SUBJECT, 20L)).isSameAs(snapshot);

        verify(jobPostingRepository).findDetail(20L);
        verify(snapshotFactory).fromJobPosting(jobPosting);
    }


    @Test
    @DisplayName("존재하지 않는 채용공고는 RAG 문서로 조회할 수 없다")
    void rejectsUnknownJobPosting() {
        when(authorizationService.requireUser(SUBJECT)).thenReturn(user);
        when(jobPostingRepository.findDetail(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJobPosting(SUBJECT, 20L))
                .isInstanceOf(JobPostingNotFoundException.class);

        verifyNoInteractions(snapshotFactory);
    }


    @Test
    @DisplayName("현재 사용자가 소유한 자기소개서의 현재 버전을 조회한다")
    void getsOwnedCurrentCoverLetterSnapshot() {
        when(authorizationService.requireUser(SUBJECT)).thenReturn(user);
        when(user.getId()).thenReturn(USER_ID);
        when(coverLetterRepository.findByIdAndUser_Id(30L, USER_ID)).thenReturn(Optional.of(coverLetter));
        when(coverLetter.getId()).thenReturn(30L);
        when(coverLetter.getCurrentVersionNumber()).thenReturn(2);
        when(versionRepository.findByCoverLetter_IdAndVersionNumber(30L, 2)).thenReturn(Optional.of(version));
        when(snapshotFactory.fromCoverLetter(coverLetter, version)).thenReturn(snapshot);

        assertThat(service.getCoverLetter(SUBJECT, 30L)).isSameAs(snapshot);

        verify(coverLetterRepository).findByIdAndUser_Id(30L, USER_ID);
        verify(versionRepository).findByCoverLetter_IdAndVersionNumber(30L, 2);
    }


    @Test
    @DisplayName("타인 소유이거나 존재하지 않는 자기소개서는 동일한 404 예외로 처리한다")
    void rejectsUnownedCoverLetter() {
        when(authorizationService.requireUser(SUBJECT)).thenReturn(user);
        when(user.getId()).thenReturn(USER_ID);
        when(coverLetterRepository.findByIdAndUser_Id(30L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCoverLetter(SUBJECT, 30L))
                .isInstanceOf(CoverLetterNotFoundException.class);

        verifyNoInteractions(versionRepository);
        verifyNoInteractions(snapshotFactory);
    }


    @Test
    @DisplayName("자기소개서의 현재 버전 행이 없으면 변환하지 않는다")
    void rejectsMissingCurrentCoverLetterVersion() {
        when(authorizationService.requireUser(SUBJECT)).thenReturn(user);
        when(user.getId()).thenReturn(USER_ID);
        when(coverLetterRepository.findByIdAndUser_Id(30L, USER_ID)).thenReturn(Optional.of(coverLetter));
        when(coverLetter.getId()).thenReturn(30L);
        when(coverLetter.getCurrentVersionNumber()).thenReturn(2);
        when(versionRepository.findByCoverLetter_IdAndVersionNumber(30L, 2)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCoverLetter(SUBJECT, 30L))
                .isInstanceOf(CoverLetterVersionNotFoundException.class);

        verifyNoInteractions(snapshotFactory);
    }


    @Test
    @DisplayName("현재 사용자가 소유한 이력서만 추출 상태와 함께 조회한다")
    void getsOwnedResumeResolution() {
        when(authorizationService.requireUser(SUBJECT)).thenReturn(user);
        when(user.getId()).thenReturn(USER_ID);
        when(resumeRepository.findByIdAndUser_Id(40L, USER_ID)).thenReturn(Optional.of(resume));
        when(snapshotFactory.fromResume(resume)).thenReturn(resolution);

        assertThat(service.getResume(SUBJECT, 40L)).isSameAs(resolution);

        verify(resumeRepository).findByIdAndUser_Id(40L, USER_ID);
        verify(snapshotFactory).fromResume(resume);
    }


    @Test
    @DisplayName("타인 소유이거나 존재하지 않는 이력서는 동일한 404 예외로 처리한다")
    void rejectsUnownedResume() {
        when(authorizationService.requireUser(SUBJECT)).thenReturn(user);
        when(user.getId()).thenReturn(USER_ID);
        when(resumeRepository.findByIdAndUser_Id(40L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getResume(SUBJECT, 40L))
                .isInstanceOf(ResumeNotFoundException.class);

        verifyNoInteractions(snapshotFactory);
    }
}
