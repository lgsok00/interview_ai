package com.interviewai.interview.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InterviewSessionSnapshotAssembler {

    private final JobPostingRepository jobPostingRepository;
    private final CoverLetterRepresentativeRepository coverLetterRepresentativeRepository;
    private final CoverLetterVersionRepository coverLetterVersionRepository;
    private final ResumeRepresentativeRepository resumeRepresentativeRepository;


    public InterviewSessionSnapshotAssembler(
            JobPostingRepository jobPostingRepository,
            CoverLetterRepresentativeRepository coverLetterRepresentativeRepository,
            CoverLetterVersionRepository coverLetterVersionRepository,
            ResumeRepresentativeRepository resumeRepresentativeRepository
    ) {
        this.jobPostingRepository = jobPostingRepository;
        this.coverLetterRepresentativeRepository = coverLetterRepresentativeRepository;
        this.coverLetterVersionRepository = coverLetterVersionRepository;
        this.resumeRepresentativeRepository = resumeRepresentativeRepository;
    }


    public InterviewSourceSnapshot assemble(Long userId, Long jobPostingId) {
        JobPosting jobPosting = jobPostingRepository.findDetail(jobPostingId)
                .orElseThrow(JobPostingNotFoundException::new);

        return new InterviewSourceSnapshot(
                toJobPostingSnapshot(jobPosting),
                findCoverLetterSnapshot(userId),
                findResumeSnapshot(userId)
        );
    }


    private InterviewSourceSnapshot.PersonalDocumentSnapshot findCoverLetterSnapshot(Long userId) {
        return coverLetterRepresentativeRepository.findDetailByUserId(userId)
                .map(this::toCoverLetterSnapshot)
                .orElse(null);
    }


    private InterviewSourceSnapshot.PersonalDocumentSnapshot findResumeSnapshot(Long userId) {
        return resumeRepresentativeRepository.findDetailByUserId(userId)
                .map(this::toResumeSnapshot)
                .orElse(null);
    }


    private InterviewSourceSnapshot.JobPostingSnapshot toJobPostingSnapshot(JobPosting jobPosting) {
        return new InterviewSourceSnapshot.JobPostingSnapshot(
                jobPosting.getId(),
                jobPosting.getCompany().getName(),
                jobPosting.getTitle(),
                jobPosting.getJobRole(),
                jobPosting.getDescription()
        );
    }


    private InterviewSourceSnapshot.PersonalDocumentSnapshot toCoverLetterSnapshot(CoverLetterRepresentative representative) {
        CoverLetter coverLetter = representative.getCoverLetter();

        CoverLetterVersion currentVersion = coverLetterVersionRepository
                .findByCoverLetter_IdAndVersionNumber(coverLetter.getId(), coverLetter.getCurrentVersionNumber())
                .orElseThrow(CoverLetterVersionNotFoundException::new);

        return new InterviewSourceSnapshot.PersonalDocumentSnapshot(
                coverLetter.getId(),
                currentVersion.getTitle(),
                currentVersion.getContent()
        );
    }


    private InterviewSourceSnapshot.PersonalDocumentSnapshot toResumeSnapshot(ResumeRepresentative representative) {
        Resume resume = representative.getResume();

        if (resume.getExtractionStatus() != ResumeExtractionStatus.COMPLETED
                || resume.getExtractedText() == null
                || resume.getExtractedText().isBlank()) {
            throw new RepresentativeResumeNotReadyException();
        }

        return new InterviewSourceSnapshot.PersonalDocumentSnapshot(
                resume.getId(),
                resume.getTitle(),
                resume.getExtractedText()
        );
    }
}
