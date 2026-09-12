package com.interviewai.interview.service;

import com.interviewai.global.security.AdminAuthorizationService;
import com.interviewai.interview.dto.CreateInterviewSessionRequest;
import com.interviewai.interview.dto.InterviewSessionResponse;
import com.interviewai.interview.entity.InterviewSession;
import com.interviewai.interview.repository.InterviewSessionRepository;
import com.interviewai.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class InterviewSessionService {

    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewSessionSnapshotAssembler snapshotAssembler;
    private final AdminAuthorizationService authorizationService;
    private final Clock catalogClock;


    public InterviewSessionService(
            InterviewSessionRepository interviewSessionRepository,
            InterviewSessionSnapshotAssembler snapshotAssembler,
            AdminAuthorizationService authorizationService,
            Clock catalogClock
    ) {
        this.interviewSessionRepository = interviewSessionRepository;
        this.snapshotAssembler = snapshotAssembler;
        this.authorizationService = authorizationService;
        this.catalogClock = catalogClock;
    }


    @Transactional
    public InterviewSessionResponse create(String subject, CreateInterviewSessionRequest request) {
        User user = authorizationService.requireUser(subject);

        InterviewSourceSnapshot snapshot = snapshotAssembler.assemble(user.getId(), request.jobPostingId());

        InterviewSourceSnapshot.JobPostingSnapshot jobPosting = snapshot.jobPosting();
        InterviewSourceSnapshot.PersonalDocumentSnapshot coverLetter = snapshot.coverLetter();
        InterviewSourceSnapshot.PersonalDocumentSnapshot resume = snapshot.resume();

        InterviewSession session = InterviewSession.create(
                user,
                jobPosting.id(),
                idOf(coverLetter),
                idOf(resume),
                jobPosting.companyName(),
                jobPosting.title(),
                jobPosting.jobRole(),
                jobPosting.content(),
                titleOf(coverLetter),
                contentOf(coverLetter),
                titleOf(resume),
                contentOf(resume),
                LocalDateTime.now(catalogClock)
        );

        return InterviewSessionResponse.from(interviewSessionRepository.save(session));
    }


    private Long idOf(InterviewSourceSnapshot.PersonalDocumentSnapshot snapshot) {
        return snapshot == null ? null : snapshot.id();
    }


    private String titleOf(InterviewSourceSnapshot.PersonalDocumentSnapshot snapshot) {
        return snapshot == null ? null : snapshot.title();
    }


    private String contentOf(InterviewSourceSnapshot.PersonalDocumentSnapshot snapshot) {
        return snapshot == null ? null : snapshot.content();
    }
}
