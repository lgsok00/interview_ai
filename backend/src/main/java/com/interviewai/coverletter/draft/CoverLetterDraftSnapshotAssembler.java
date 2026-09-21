package com.interviewai.coverletter.draft;

import com.interviewai.coverletter.entity.CoverLetter;
import com.interviewai.coverletter.entity.CoverLetterVersion;
import com.interviewai.coverletter.exception.CoverLetterNotFoundException;
import com.interviewai.coverletter.exception.CoverLetterVersionNotFoundException;
import com.interviewai.coverletter.repository.CoverLetterRepository;
import com.interviewai.coverletter.repository.CoverLetterVersionRepository;
import com.interviewai.interview.exception.RepresentativeResumeNotReadyException;
import com.interviewai.jobposting.entity.JobPosting;
import com.interviewai.jobposting.exception.JobPostingNotFoundException;
import com.interviewai.jobposting.repository.JobPostingRepository;
import com.interviewai.resume.entity.Resume;
import com.interviewai.resume.entity.ResumeRepresentative;
import com.interviewai.resume.enums.ResumeExtractionStatus;
import com.interviewai.resume.exception.ResumeNotFoundException;
import com.interviewai.resume.repository.ResumeRepository;
import com.interviewai.resume.repository.ResumeRepresentativeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CoverLetterDraftSnapshotAssembler {

    private final CoverLetterRepository coverLetterRepository;
    private final CoverLetterVersionRepository versionRepository;
    private final JobPostingRepository jobPostingRepository;
    private final ResumeRepository resumeRepository;
    private final ResumeRepresentativeRepository representativeRepository;
    private final CoverLetterDraftInputHasher inputHasher;


    public CoverLetterDraftSnapshotAssembler(
            CoverLetterRepository coverLetterRepository,
            CoverLetterVersionRepository versionRepository,
            JobPostingRepository jobPostingRepository,
            ResumeRepository resumeRepository,
            ResumeRepresentativeRepository representativeRepository,
            CoverLetterDraftInputHasher inputHasher
    ) {
        this.coverLetterRepository = coverLetterRepository;
        this.versionRepository = versionRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.resumeRepository = resumeRepository;
        this.representativeRepository = representativeRepository;
        this.inputHasher = inputHasher;
    }


    @Transactional
    public PreparedInput assemble(Long userId, Long coverLetterId, CreateCoverLetterDraftRequest request) {
        CoverLetter coverLetter = coverLetterRepository.findOwnedForUpdate(coverLetterId, userId)
                .orElseThrow(CoverLetterNotFoundException::new);

        CoverLetterVersion currentVersion = versionRepository
                .findByCoverLetter_IdAndVersionNumber(coverLetterId, coverLetter.getCurrentVersionNumber())
                .orElseThrow(CoverLetterVersionNotFoundException::new);

        JobPosting jobPosting = jobPostingRepository.findDetail(request.jobPostingId())
                .orElseThrow(JobPostingNotFoundException::new);

        Resume resume = findResume(userId, request.resumeId());

        CoverLetterDraft.InputSnapshot input = toSnapshot(
                coverLetter,
                currentVersion,
                jobPosting,
                resume,
                request.instruction()
        );

        return new PreparedInput(coverLetter, input, inputHasher.hash(input));
    }


    private Resume findResume(Long userId, Long requestedResumeId) {
        Resume resume;

        if (requestedResumeId != null) {
            resume = resumeRepository.findByIdAndUser_Id(requestedResumeId, userId)
                    .orElseThrow(ResumeNotFoundException::new);

        } else {
            resume = representativeRepository.findDetailByUserId(userId)
                    .map(ResumeRepresentative::getResume)
                    .orElseThrow(RepresentativeResumeNotReadyException::new);
        }

        if (resume.getExtractionStatus() != ResumeExtractionStatus.COMPLETED
                || resume.getExtractedText() == null
                || resume.getExtractedText().isBlank()) {
            throw new RepresentativeResumeNotReadyException();
        }

        return resume;
    }


    private CoverLetterDraft.InputSnapshot toSnapshot(
            CoverLetter coverLetter,
            CoverLetterVersion currentVersion,
            JobPosting jobPosting,
            Resume resume,
            String instruction
    ) {
        var company = jobPosting.getCompany();

        return new CoverLetterDraft.InputSnapshot(
                jobPosting.getId(),
                resume.getId(),
                coverLetter.getCurrentVersionNumber(),
                currentVersion.getTitle(),
                currentVersion.getContent(),

                company.getId(),
                company.getName(),
                company.getIndustry(),
                company.getDescription(),
                company.getWebsiteUrl(),
                company.getLocation(),

                jobPosting.getTitle(),
                jobPosting.getJobRole(),
                jobPosting.getEmploymentType().name(),
                jobPosting.getLocation(),
                jobPosting.getDescription(),
                jobPosting.getSourceUrl(),
                jobPosting.getOpensAt(),
                jobPosting.getClosesAt(),

                resume.getTitle(),
                resume.getExtractedText(),
                instruction
        );
    }


    public record PreparedInput(CoverLetter coverLetter, CoverLetterDraft.InputSnapshot input, String inputHash) {

    }
}
