package com.interviewai.interview.service;

import com.interviewai.global.error.CatalogException;
import com.interviewai.global.security.AdminAuthorizationService;
import com.interviewai.global.validation.CatalogInput;
import com.interviewai.interview.dto.CreateInterviewSessionRequest;
import com.interviewai.interview.dto.InterviewQuestionResponse;
import com.interviewai.interview.dto.InterviewSessionPageResponse;
import com.interviewai.interview.dto.InterviewSessionResponse;
import com.interviewai.interview.entity.InterviewSession;
import com.interviewai.interview.enums.InterviewSessionStatus;
import com.interviewai.interview.generation.InterviewGenerationExecutionService;
import com.interviewai.interview.repository.InterviewQuestionRepository;
import com.interviewai.interview.repository.InterviewSessionRepository;
import com.interviewai.user.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class InterviewSessionService {

    private static final Set<InterviewSessionStatus> QUESTION_AVAILABLE_STATUSES = Set.of(
            InterviewSessionStatus.READY,
            InterviewSessionStatus.IN_PROGRESS,
            InterviewSessionStatus.COMPLETED
    );

    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewQuestionRepository interviewQuestionRepository;
    private final InterviewSessionSnapshotAssembler snapshotAssembler;
    private final AdminAuthorizationService authorizationService;
    private final InterviewGenerationExecutionService generationExecutionService;
    private final Clock catalogClock;


    public InterviewSessionService(
            InterviewSessionRepository interviewSessionRepository,
            InterviewQuestionRepository interviewQuestionRepository,
            InterviewSessionSnapshotAssembler snapshotAssembler,
            AdminAuthorizationService authorizationService,
            InterviewGenerationExecutionService generationExecutionService,
            Clock catalogClock
    ) {
        this.interviewSessionRepository = interviewSessionRepository;
        this.interviewQuestionRepository = interviewQuestionRepository;
        this.snapshotAssembler = snapshotAssembler;
        this.authorizationService = authorizationService;
        this.generationExecutionService = generationExecutionService;
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
                jobPosting.companyId(),
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

        InterviewSession savedSession = interviewSessionRepository.save(session);
        generationExecutionService.register(savedSession.getId());

        return InterviewSessionResponse.from(savedSession);
    }


    public InterviewSessionPageResponse getAll(String subject, int page, int size) {
        User user = authorizationService.requireUser(subject);

        return InterviewSessionPageResponse.from(
                interviewSessionRepository.findAllByUser_IdOrderByCreatedAtDescIdDesc(
                        user.getId(), CatalogInput.page(page, size)
                )
        );
    }


    public InterviewSessionResponse get(String subject, long sessionId) {
        User user = authorizationService.requireUser(subject);

        return InterviewSessionResponse.from(findOwnedSession(user.getId(), sessionId));
    }


    public List<InterviewQuestionResponse> getQuestions(String subject, long sessionId) {
        User user = authorizationService.requireUser(subject);
        InterviewSession session = findOwnedSession(user.getId(), sessionId);

        if (!QUESTION_AVAILABLE_STATUSES.contains(session.getStatus())) {
            throw conflict("질문 생성이 완료된 면접 세션에서만 질문을 조회할 수 있습니다.");
        }

        return interviewQuestionRepository
                .findAllBySession_IdOrderBySequenceNumberAsc(session.getId())
                .stream()
                .map(InterviewQuestionResponse::from)
                .toList();
    }


    @Transactional
    public InterviewSessionResponse start(String subject, long sessionId) {
        User user = authorizationService.requireUser(subject);
        InterviewSession session = findOwnedSessionForUpdate(user.getId(), sessionId);

        if (session.getStatus() != InterviewSessionStatus.READY) {
            throw conflict("준비된 면접 세션만 시작할 수 있습니다.");
        }

        session.start(now());

        return InterviewSessionResponse.from(session);
    }


    @Transactional
    public InterviewSessionResponse complete(String subject, long sessionId) {
        User user = authorizationService.requireUser(subject);
        InterviewSession session = findOwnedSessionForUpdate(user.getId(), sessionId);

        if (session.getStatus() != InterviewSessionStatus.IN_PROGRESS) {
            throw conflict("진행 중인 면접 세션만 완료할 수 있습니다.");
        }

        session.complete(now());

        return InterviewSessionResponse.from(session);
    }


    public void retryGeneration(String subject, long sessionId) {
        User user = authorizationService.requireUser(subject);
        generationExecutionService.retry(user.getId(), sessionId);
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


    private InterviewSession findOwnedSession(Long userId, long sessionId) {
        return interviewSessionRepository.findByIdAndUser_Id(sessionId, userId).orElseThrow(this::notFound);
    }


    private InterviewSession findOwnedSessionForUpdate(Long userId, long sessionId) {
        return interviewSessionRepository.findOwnedByIdForUpdate(sessionId, userId).orElseThrow(this::notFound);
    }


    private LocalDateTime now() {
        return LocalDateTime.now(catalogClock);
    }


    private CatalogException notFound() {
        return new CatalogException(HttpStatus.NOT_FOUND, "INTERVIEW_SESSION_NOT_FOUND", "면접 세션을 찾을 수 없습니다.");
    }


    private CatalogException conflict(String message) {
        return new CatalogException(HttpStatus.CONFLICT, "INTERVIEW_SESSION_CONFLICT", message);
    }
}
