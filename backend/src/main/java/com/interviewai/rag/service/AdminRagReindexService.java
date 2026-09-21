package com.interviewai.rag.service;

import com.interviewai.company.entity.Company;
import com.interviewai.company.repository.CompanyRepository;
import com.interviewai.coverletter.entity.CoverLetter;
import com.interviewai.coverletter.entity.CoverLetterVersion;
import com.interviewai.coverletter.repository.CoverLetterRepository;
import com.interviewai.coverletter.repository.CoverLetterVersionRepository;
import com.interviewai.global.error.CatalogException;
import com.interviewai.global.security.AdminAuthorizationService;
import com.interviewai.global.validation.CatalogInput;
import com.interviewai.jobposting.entity.JobPosting;
import com.interviewai.jobposting.repository.JobPostingRepository;
import com.interviewai.rag.document.RagSourceResolution;
import com.interviewai.rag.document.RagSourceSnapshot;
import com.interviewai.rag.document.RagSourceSnapshotFactory;
import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.dto.AdminRagResponse;
import com.interviewai.rag.entity.RagIndexJobEntity;
import com.interviewai.rag.index.RagIndexTarget;
import com.interviewai.rag.repository.AdminRagRepository;
import com.interviewai.resume.entity.Resume;
import com.interviewai.resume.repository.ResumeRepository;
import com.interviewai.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminRagReindexService {

    private final AdminAuthorizationService authorizationService;
    private final CompanyRepository companyRepository;
    private final JobPostingRepository jobPostingRepository;
    private final CoverLetterRepository coverLetterRepository;
    private final CoverLetterVersionRepository versionRepository;
    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;
    private final RagSourceSnapshotFactory snapshotFactory;
    private final RagIndexJobRegistrationService registrationService;
    private final AdminRagRepository adminRepository;


    private static RuntimeException sourceNotFound() {
        return new CatalogException(
                HttpStatus.NOT_FOUND,
                "RAG_SOURCE_NOT_FOUND",
                "현재 원본을 찾을 수 없습니다."
        );
    }


    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminRagResponse.Job reindex(String subject, RagSourceType sourceType, Long sourceId) {
        authorizationService.requireAdmin(subject);
        CatalogInput.id(sourceId, "sourceId");

        if (sourceType == null) {
            throw CatalogException.invalid("sourceType", "필수 값입니다.");
        }

        RagSourceSnapshot snapshot = switch (sourceType) {
            case COMPANY -> companySnapshot(sourceId);
            case JOB_POSTING -> jobPostingSnapshot(sourceId);
            case COVER_LETTER -> coverLetterSnapshot(sourceId);
            case RESUME -> resumeSnapshot(sourceId);
        };

        RagIndexJobEntity job = registrationService.registerUpsert(
                new RagIndexTarget(snapshot, RagSourceChangeRegistrationService.PIPELINE_VERSION),
                RagSourceChangeRegistrationService.MAX_ATTEMPTS
        );

        return adminRepository.findJob(job.getId())
                .orElseThrow(() -> new IllegalStateException("등록한 RAG 작업을 찾을 수 없습니다."));
    }


    private RagSourceSnapshot companySnapshot(Long sourceId) {
        Company company = companyRepository.findLockedById(sourceId)
                .orElseThrow(AdminRagReindexService::sourceNotFound);

        return snapshotFactory.fromCompany(company);
    }


    private RagSourceSnapshot jobPostingSnapshot(Long sourceId) {
        Long companyId = jobPostingRepository.findCompanyId(sourceId)
                .orElseThrow(AdminRagReindexService::sourceNotFound);

        companyRepository.findLockedById(companyId)
                .orElseThrow(AdminRagReindexService::sourceNotFound);

        JobPosting jobPosting = jobPostingRepository.findDetailForUpdate(sourceId)
                .orElseThrow(AdminRagReindexService::sourceNotFound);

        return snapshotFactory.fromJobPosting(jobPosting);
    }


    private RagSourceSnapshot coverLetterSnapshot(Long sourceId) {
        Long ownerId = coverLetterRepository.findOwnedId(sourceId)
                .orElseThrow(AdminRagReindexService::sourceNotFound);

        lockOwner(ownerId);

        CoverLetter coverLetter = coverLetterRepository.findOwnedForUpdate(sourceId, ownerId)
                .orElseThrow(AdminRagReindexService::sourceNotFound);

        CoverLetterVersion version = versionRepository
                .findByCoverLetter_IdAndVersionNumber(sourceId, coverLetter.getCurrentVersionNumber())
                .orElseThrow(() -> new CatalogException(
                        HttpStatus.CONFLICT,
                        "RAG_SOURCE_NOT_READY",
                        "자기소개서의 현재 버전을 찾을 수 없습니다."
                ));

        return snapshotFactory.fromCoverLetter(coverLetter, version);
    }


    private RagSourceSnapshot resumeSnapshot(Long sourceId) {
        Long ownerId = resumeRepository.findOwnedId(sourceId)
                .orElseThrow(AdminRagReindexService::sourceNotFound);

        lockOwner(ownerId);

        Resume resume = resumeRepository.findOwnedForUpdate(sourceId, ownerId)
                .orElseThrow(AdminRagReindexService::sourceNotFound);

        RagSourceResolution resolution = snapshotFactory.fromResume(resume);

        if (!resolution.isReady()) {
            throw new CatalogException(
                    HttpStatus.CONFLICT,
                    "RAG_SOURCE_NOT_READY",
                    "이력서의 추출 본문이 준비되지 않았습니다."
            );
        }

        return resolution.snapshot();
    }


    private void lockOwner(Long ownerId) {
        userRepository.findByIdForUpdate(ownerId)
                .orElseThrow(AdminRagReindexService::sourceNotFound);
    }
}
