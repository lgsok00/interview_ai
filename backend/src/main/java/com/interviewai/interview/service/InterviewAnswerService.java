package com.interviewai.interview.service;

import com.interviewai.global.error.CatalogException;
import com.interviewai.global.security.AdminAuthorizationService;
import com.interviewai.global.validation.CatalogInput;
import com.interviewai.interview.dto.InterviewAnswerResponse;
import com.interviewai.interview.dto.InterviewFollowUpResponse;
import com.interviewai.interview.dto.SubmitInterviewAnswerRequest;
import com.interviewai.interview.entity.InterviewAnswer;
import com.interviewai.interview.entity.InterviewQuestion;
import com.interviewai.interview.entity.InterviewSession;
import com.interviewai.interview.enums.InterviewQuestionType;
import com.interviewai.interview.enums.InterviewSessionStatus;
import com.interviewai.interview.generation.InterviewFollowUpGenerator;
import com.interviewai.interview.repository.InterviewAnswerRepository;
import com.interviewai.interview.repository.InterviewQuestionRepository;
import com.interviewai.interview.repository.InterviewSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class InterviewAnswerService {

    private final InterviewSessionRepository sessions;
    private final InterviewQuestionRepository questions;
    private final InterviewAnswerRepository answers;
    private final AdminAuthorizationService authorization;
    private final Clock catalogClock;


    public InterviewAnswerService(
            InterviewSessionRepository sessions,
            InterviewQuestionRepository questions,
            InterviewAnswerRepository answers,
            AdminAuthorizationService authorization,
            Clock catalogClock
    ) {
        this.sessions = sessions;
        this.questions = questions;
        this.answers = answers;
        this.authorization = authorization;
        this.catalogClock = catalogClock;
    }


    @Transactional
    public InterviewAnswerResponse submit(
            String subject,
            long sessionId,
            long questionId,
            SubmitInterviewAnswerRequest request
    ) {
        InterviewSession session = owned(subject, sessionId, true);
        InterviewQuestion question = question(sessionId, questionId);
        String content = CatalogInput.text(request.content(), "content", 10000, true);

        InterviewAnswer existing = answers.findByQuestion_Id(questionId).orElse(null);

        if (existing != null) {
            if (!existing.getContent().equals(content)) {
                throw conflict("INTERVIEW_ANSWER_CONFLICT", "이미 제출한 답변은 변경할 수 없습니다.");
            }

            return response(existing);
        }

        requireInProgress(session);

        InterviewAnswer saved = answers.save(InterviewAnswer.create(question, content, LocalDateTime.now(catalogClock)));

        return response(saved);
    }


    public List<InterviewAnswerResponse> getAll(String subject, long sessionId) {
        owned(subject, sessionId, false);

        Map<Long, Long> followUps = new HashMap<>();

        for (InterviewQuestion question : questions.findAllBySession_IdOrderBySequenceNumberAsc(sessionId)) {
            if (question.getParentQuestionId() != null) {
                followUps.put(question.getParentQuestionId(), question.getId());
            }
        }

        return answers.findAllBySessionId(sessionId)
                .stream()
                .map(answer -> InterviewAnswerResponse.from(
                        answer,
                        followUps.get(answer.getQuestion().getId())
                ))
                .toList();
    }


    @Transactional
    public Preparation prepareFollowUp(String subject, long sessionId, long questionId) {
        InterviewSession session = owned(subject, sessionId, true);
        InterviewQuestion parent = question(sessionId, questionId);
        requireInitial(parent);

        InterviewQuestion existing = questions.findByParentQuestionId(questionId).orElse(null);

        if (existing != null) {
            return new Preparation(null, InterviewFollowUpResponse.from(existing));
        }

        requireInProgress(session);

        InterviewAnswer answer = answers.findByQuestion_Id(questionId)
                .orElseThrow(() -> conflict(
                        "INTERVIEW_ANSWER_REQUIRED",
                        "답변을 제출한 뒤 꼬리 질문을 생성할 수 있습니다."
                ));

        return new Preparation(
                new InterviewFollowUpGenerator.Input(
                        session.getJobRole(),
                        parent.getContent(),
                        answer.getContent()
                ),
                null
        );
    }


    @Transactional
    public InterviewFollowUpResponse saveFollowUp(
            String subject,
            long sessionId,
            long questionId,
            InterviewFollowUpGenerator.Generated generated
    ) {
        InterviewSession session = owned(subject, sessionId, true);
        InterviewQuestion parent = question(sessionId, questionId);
        requireInitial(parent);

        InterviewQuestion existing = questions.findByParentQuestionId(questionId).orElse(null);

        if (existing != null) {
            return InterviewFollowUpResponse.from(existing);
        }

        requireInProgress(session);

        if (answers.findByQuestion_Id(questionId).isEmpty()) {
            throw conflict("INTERVIEW_ANSWER_REQUIRED", "저장된 답변이 필요합니다.");
        }

        int lastSequence = questions.findFirstBySession_IdOrderBySequenceNumberDesc(sessionId)
                .map(InterviewQuestion::getSequenceNumber)
                .orElse(0);

        InterviewQuestion saved = questions.save(
                InterviewQuestion.createFollowUp(
                        parent,
                        Math.addExact(lastSequence, 1),
                        generated.source(),
                        generated.content(),
                        generated.contextSnapshot(),
                        LocalDateTime.now(catalogClock)
                )
        );

        return InterviewFollowUpResponse.from(saved);
    }


    private InterviewSession owned(String subject, long sessionId, boolean forUpdate) {
        Long userId = authorization.requireUser(subject).getId();

        return (forUpdate
                ? sessions.findOwnedByIdForUpdate(sessionId, userId)
                : sessions.findByIdAndUser_Id(sessionId, userId))
                .orElseThrow(() -> new CatalogException(
                        HttpStatus.NOT_FOUND,
                        "INTERVIEW_SESSION_NOT_FOUND",
                        "면접 세션을 찾을 수 없습니다."
                ));
    }


    private InterviewQuestion question(long sessionId, long questionId) {
        return questions.findByIdAndSession_Id(questionId, sessionId)
                .orElseThrow(() -> new CatalogException(
                        HttpStatus.NOT_FOUND,
                        "INTERVIEW_QUESTION_NOT_FOUND",
                        "면접 질문을 찾을 수 없습니다."
                ));
    }


    private InterviewAnswerResponse response(InterviewAnswer answer) {
        Long followUpId = questions.findByParentQuestionId(answer.getQuestion().getId())
                .map(InterviewQuestion::getId)
                .orElse(null);

        return InterviewAnswerResponse.from(answer, followUpId);
    }


    private void requireInProgress(InterviewSession session) {
        if (session.getStatus() != InterviewSessionStatus.IN_PROGRESS) {
            throw conflict(
                    "INTERVIEW_SESSION_CONFLICT",
                    "진행 중인 면접에서만 답변이나 꼬리 질문을 추가할 수 있습니다."
            );
        }
    }


    private void requireInitial(InterviewQuestion question) {
        if (question.getQuestionType() == InterviewQuestionType.FOLLOW_UP || question.getParentQuestionId() != null) {
            throw conflict(
                    "INTERVIEW_FOLLOW_UP_DEPTH_EXCEEDED",
                    "꼬리 질문에는 추가 꼬리 질문을 생성할 수 없습니다."
            );
        }
    }


    private CatalogException conflict(String code, String message) {
        return new CatalogException(HttpStatus.CONFLICT, code, message);
    }


    public record Preparation(InterviewFollowUpGenerator.Input input, InterviewFollowUpResponse existing) {

    }
}
