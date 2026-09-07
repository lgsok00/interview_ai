package com.interviewai.rag.service;

import com.interviewai.company.entity.Company;
import com.interviewai.coverletter.entity.CoverLetter;
import com.interviewai.coverletter.entity.CoverLetterVersion;
import com.interviewai.jobposting.entity.JobPosting;
import com.interviewai.rag.document.*;
import com.interviewai.rag.index.RagIndexTarget;
import com.interviewai.resume.entity.Resume;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class RagSourceChangeRegistrationService {

    private static final String PIPELINE_VERSION = "rag-v1";
    private static final int MAX_ATTEMPTS = 3;

    private final RagSourceSnapshotFactory snapshotFactory;
    private final RagIndexJobRegistrationService jobRegistrationService;


    public RagSourceChangeRegistrationService(
            RagSourceSnapshotFactory snapshotFactory,
            RagIndexJobRegistrationService jobRegistrationService
    ) {
        this.snapshotFactory = snapshotFactory;
        this.jobRegistrationService = jobRegistrationService;
    }


    @Transactional(propagation = Propagation.MANDATORY)
    public void registerCompanyUpsert(Company company) {
        registerUpsert(snapshotFactory.fromCompany(company));
    }


    @Transactional(propagation = Propagation.MANDATORY)
    public void registerJobPostingUpsert(JobPosting jobPosting) {
        registerUpsert(snapshotFactory.fromJobPosting(jobPosting));
    }


    @Transactional(propagation = Propagation.MANDATORY)
    public void registerCoverLetterUpsert(CoverLetter coverLetter, CoverLetterVersion currentVersion) {
        registerUpsert(snapshotFactory.fromCoverLetter(coverLetter, currentVersion));
    }


    @Transactional(propagation = Propagation.MANDATORY)
    public void registerResumeChange(Resume resume) {
        Objects.requireNonNull(resume, "resume은 필수입니다.");

        RagSourceResolution resolution = snapshotFactory.fromResume(resume);

        if (resolution.isReady()) {
            registerUpsert(resolution.snapshot());

            return;
        }

        jobRegistrationService.registerDelete(resolution.sourceKey(), MAX_ATTEMPTS);
    }


    @Transactional(propagation = Propagation.MANDATORY)
    public void registerDelete(RagSourceType sourceType, Long sourceId) {
        jobRegistrationService.registerDelete(new RagSourceKey(sourceType, sourceId), MAX_ATTEMPTS);
    }


    private void registerUpsert(RagSourceSnapshot snapshot) {
        jobRegistrationService.registerUpsert(new RagIndexTarget(snapshot, PIPELINE_VERSION), MAX_ATTEMPTS);
    }
}
