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
import com.interviewai.global.validation.CatalogInput;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RagSourceAccessService {

    private final AdminAuthorizationService authorizationService;
    private final CompanyRepository companyRepository;
    private final JobPostingRepository jobPostingRepository;
    private final CoverLetterRepository coverLetterRepository;
    private final CoverLetterVersionRepository versionRepository;
    private final ResumeRepository resumeRepository;
    private final RagSourceSnapshotFactory snapshotFactory;


    public RagSourceAccessService(
            AdminAuthorizationService authorizationService,
            CompanyRepository companyRepository,
            JobPostingRepository jobPostingRepository,
            CoverLetterRepository coverLetterRepository,
            CoverLetterVersionRepository versionRepository,
            ResumeRepository resumeRepository,
            RagSourceSnapshotFactory snapshotFactory
    ) {
        this.authorizationService = authorizationService;
        this.companyRepository = companyRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.coverLetterRepository = coverLetterRepository;
        this.versionRepository = versionRepository;
        this.resumeRepository = resumeRepository;
        this.snapshotFactory = snapshotFactory;
    }


    public RagSourceSnapshot getCompany(String subject, Long companyId) {
        authorizationService.requireUser(subject);

        Long validatedCompanyId = CatalogInput.id(companyId, "companyId");

        Company company = companyRepository.findById(validatedCompanyId).orElseThrow(CompanyNotFoundException::new);

        return snapshotFactory.fromCompany(company);
    }


    public RagSourceSnapshot getJobPosting(String subject, Long jobPostingId) {
        authorizationService.requireUser(subject);

        Long validatedJobPostingId = CatalogInput.id(jobPostingId, "jobPostingId");

        JobPosting jobPosting = jobPostingRepository.findDetail(validatedJobPostingId)
                .orElseThrow(JobPostingNotFoundException::new);

        return snapshotFactory.fromJobPosting(jobPosting);
    }


    public RagSourceSnapshot getCoverLetter(String subject, Long coverLetterId) {
        User user = authorizationService.requireUser(subject);

        Long validatedCoverLetterId = CatalogInput.id(coverLetterId, "coverLetterId");

        CoverLetter coverLetter = coverLetterRepository
                .findByIdAndUser_Id(validatedCoverLetterId, user.getId())
                .orElseThrow(CoverLetterNotFoundException::new);

        CoverLetterVersion currentVersion = versionRepository
                .findByCoverLetter_IdAndVersionNumber(coverLetter.getId(), coverLetter.getCurrentVersionNumber())
                .orElseThrow(CoverLetterVersionNotFoundException::new);

        return snapshotFactory.fromCoverLetter(coverLetter, currentVersion);
    }


    public RagSourceResolution getResume(String subject, Long resumeId) {
        User user = authorizationService.requireUser(subject);

        Long validatedResumeId = CatalogInput.id(resumeId, "resumeId");

        Resume resume = resumeRepository.findByIdAndUser_Id(validatedResumeId, user.getId())
                .orElseThrow(ResumeNotFoundException::new);

        return snapshotFactory.fromResume(resume);
    }
}
