package com.interviewai.interview.service;

import com.interviewai.global.error.CatalogException;
import com.interviewai.global.security.AdminAuthorizationService;
import com.interviewai.interview.dto.AnswerEvaluationResponse;
import com.interviewai.interview.entity.InterviewAnswer;
import com.interviewai.interview.entity.InterviewAnswerEvaluation;
import com.interviewai.interview.entity.InterviewSession;
import com.interviewai.interview.enums.InterviewSessionStatus;
import com.interviewai.interview.evaluation.AnswerEvaluationPolicy;
import com.interviewai.interview.evaluation.AnswerEvaluationProperties;
import com.interviewai.interview.repository.InterviewAnswerEvaluationRepository;
import com.interviewai.interview.repository.InterviewAnswerRepository;
import com.interviewai.interview.repository.InterviewSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class AnswerEvaluationService {

    private final InterviewSessionRepository sessions;
    private final InterviewAnswerRepository answers;
    private final InterviewAnswerEvaluationRepository evaluations;
    private final AdminAuthorizationService authorization;
    private final AnswerEvaluationProperties properties;
    private final Clock catalogClock;


    public AnswerEvaluationService(
            InterviewSessionRepository sessions,
            InterviewAnswerRepository answers,
            InterviewAnswerEvaluationRepository evaluations,
            AdminAuthorizationService authorization,
            AnswerEvaluationProperties properties,
            Clock catalogClock
    ) {
        this.sessions = sessions;
        this.answers = answers;
        this.evaluations = evaluations;
        this.authorization = authorization;
        this.properties = properties;
        this.catalogClock = catalogClock;
    }


    @Transactional
    public AnswerEvaluationResponse request(String subject, long sessionId, long answerId) {
        InterviewSession session = owned(subject, sessionId, true);
        requireEvaluable(session);

        InterviewAnswer answer = answer(sessionId, answerId);

        InterviewAnswerEvaluation existing = evaluations.findByAnswerIdForUpdate(answerId).orElse(null);

        if (existing != null) {
            return AnswerEvaluationResponse.from(existing);
        }

        if (!properties.enabled()) {
            throw new CatalogException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "ANSWER_EVALUATION_DISABLED",
                    "답변 평가 작업이 비활성화되어 있습니다."
            );
        }

        LocalDateTime now = LocalDateTime.now(catalogClock);

        InterviewAnswerEvaluation created =
                InterviewAnswerEvaluation.create(
                        answer,
                        properties.mode(),
                        properties.model(),
                        AnswerEvaluationPolicy.PIPELINE_VERSION,
                        now
                );

        return AnswerEvaluationResponse.from(evaluations.save(created));
    }


    public AnswerEvaluationResponse get(String subject, long sessionId, long answerId) {
        owned(subject, sessionId, false);
        answer(sessionId, answerId);

        InterviewAnswerEvaluation evaluation =
                evaluations.findByAnswer_Id(answerId).orElseThrow(this::evaluationNotFound);

        return AnswerEvaluationResponse.from(evaluation);
    }


    @Transactional
    public AnswerEvaluationResponse retry(String subject, long sessionId, long answerId) {
        InterviewSession session = owned(subject, sessionId, true);
        requireEvaluable(session);
        answer(sessionId, answerId);

        InterviewAnswerEvaluation evaluation =
                evaluations.findByAnswerIdForUpdate(answerId).orElseThrow(this::evaluationNotFound);

        if (!properties.enabled()) {
            throw new CatalogException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "ANSWER_EVALUATION_DISABLED",
                    "답변 평가 작업이 비활성화되어 있습니다."
            );
        }

        try {
            evaluation.manualRetry(LocalDateTime.now(catalogClock));

        } catch (IllegalStateException exception) {
            throw conflict("ANSWER_EVALUATION_RETRY_CONFLICT", exception.getMessage());
        }

        return AnswerEvaluationResponse.from(evaluation);
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


    private InterviewAnswer answer(long sessionId, long answerId) {
        InterviewAnswer answer = answers.findById(answerId)
                .orElseThrow(() -> new CatalogException(
                        HttpStatus.NOT_FOUND,
                        "INTERVIEW_ANSWER_NOT_FOUND",
                        "면접 답변을 찾을 수 없습니다."
                ));

        if (!answer.getQuestion().getSession().getId().equals(sessionId)) {
            throw new CatalogException(
                    HttpStatus.NOT_FOUND,
                    "INTERVIEW_ANSWER_NOT_FOUND",
                    "면접 답변을 찾을 수 없습니다."
            );
        }

        return answer;
    }


    private void requireEvaluable(InterviewSession session) {
        if (session.getStatus() != InterviewSessionStatus.IN_PROGRESS
                && session.getStatus() != InterviewSessionStatus.COMPLETED) {
            throw conflict("ANSWER_EVALUATION_SESSION_CONFLICT", "진행 중이거나 완료된 면접의 답변만 평가할 수 있습니다.");
        }
    }


    private CatalogException evaluationNotFound() {
        return new CatalogException(
                HttpStatus.NOT_FOUND,
                "ANSWER_EVALUATION_NOT_FOUND",
                "답변 평가를 찾을 수 없습니다."
        );
    }


    private CatalogException conflict(String code, String message) {
        return new CatalogException(HttpStatus.CONFLICT, code, message);
    }
}
