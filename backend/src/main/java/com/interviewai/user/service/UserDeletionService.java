package com.interviewai.user.service;

import com.interviewai.ai.usage.AiUsageLifecycleService;
import com.interviewai.coverletter.entity.CoverLetter;
import com.interviewai.coverletter.repository.CoverLetterRepository;
import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.service.RagSourceChangeRegistrationService;
import com.interviewai.resume.entity.Resume;
import com.interviewai.resume.repository.ResumeRepository;
import com.interviewai.resume.storage.ResumeFileTransactionCleanup;
import com.interviewai.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserDeletionService {

    private final UserRepository userRepository;
    private final CoverLetterRepository coverLetterRepository;
    private final ResumeRepository resumeRepository;
    private final RagSourceChangeRegistrationService ragRegistrationService;
    private final ResumeFileTransactionCleanup resumeFileCleanup;
    private final AiUsageLifecycleService lifecycle;


    public UserDeletionService(
            UserRepository userRepository,
            CoverLetterRepository coverLetterRepository,
            ResumeRepository resumeRepository,
            RagSourceChangeRegistrationService ragRegistrationService,
            ResumeFileTransactionCleanup resumeFileCleanup,
            AiUsageLifecycleService lifecycle
    ) {
        this.userRepository = userRepository;
        this.coverLetterRepository = coverLetterRepository;
        this.resumeRepository = resumeRepository;
        this.ragRegistrationService = ragRegistrationService;
        this.resumeFileCleanup = resumeFileCleanup;
        this.lifecycle = lifecycle;
    }


    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteLocked(long userId) {
        lifecycle.lockExecution();

        List<CoverLetter> coverLetters = coverLetterRepository.findAllOwnedForUpdate(userId);
        List<Resume> resumes = resumeRepository.findAllOwnedForUpdate(userId);

        coverLetters.forEach(coverLetter ->
                ragRegistrationService.registerDelete(RagSourceType.COVER_LETTER, coverLetter.getId())
        );

        resumes.forEach(resume -> ragRegistrationService.registerDelete(RagSourceType.RESUME, resume.getId()));

        resumes.stream()
                .map(Resume::getStorageKey)
                .forEach(resumeFileCleanup::deleteAfterCommit);

        userRepository.flush();

        lifecycle.cancelUser(userId);

        userRepository.deleteAllByIdInBatch(List.of(userId));
    }
}
