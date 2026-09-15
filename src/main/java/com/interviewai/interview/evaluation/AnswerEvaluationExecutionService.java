package com.interviewai.interview.evaluation;

import com.interviewai.interview.entity.InterviewAnswerEvaluation;
import com.interviewai.interview.enums.AnswerEvaluationStatus;
import com.interviewai.interview.generation.InterviewGenerationPolicy;
import com.interviewai.interview.repository.InterviewAnswerEvaluationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class AnswerEvaluationExecutionService {

    private final InterviewAnswerEvaluationRepository evaluations;
    private final JdbcTemplate jdbc;


    public AnswerEvaluationExecutionService(InterviewAnswerEvaluationRepository evaluations, JdbcTemplate jdbc) {
        this.evaluations = evaluations;
        this.jdbc = jdbc;
    }


    public boolean recoverOne() {
        InterviewAnswerEvaluation evaluation = evaluations.findNextExpiredForUpdate().orElse(null);

        if (evaluation == null) {
            return false;
        }

        LocalDateTime now = databaseNow();

        LocalDateTime availableAt =
                evaluation.getAttemptCount() < InterviewAnswerEvaluation.MAX_ATTEMPTS
                        ? now.plus(AnswerEvaluationPolicy.retryDelay(evaluation.getAttemptCount()))
                        : now;

        evaluation.expireLease(
                "ANSWER_EVALUATION_LEASE_EXPIRED",
                availableAt,
                now
        );

        return true;
    }


    public Optional<Claim> claimNext() {
        InterviewAnswerEvaluation evaluation = evaluations.findNextPendingForUpdate().orElse(null);

        if (evaluation == null) {
            return Optional.empty();
        }

        LocalDateTime now = databaseNow();
        String attemptId = UUID.randomUUID().toString();

        evaluation.claim(attemptId, now, now.plusSeconds(AnswerEvaluationPolicy.LEASE_SECONDS));

        var answer = evaluation.getAnswer();
        var question = answer.getQuestion();
        var session = question.getSession();

        return Optional.of(new Claim(
                evaluation.getId(),
                attemptId,
                evaluation.getMode(),
                evaluation.getModel(),
                evaluation.getPipelineVersion(),
                new AnswerEvaluationGenerator.Input(
                        session.getJobRole(),
                        session.getJobPostingTitle(),
                        session.getJobPostingContent(),
                        question.getContent(),
                        answer.getContent()
                )
        ));
    }


    public boolean complete(Claim claim, AnswerEvaluationGenerator.Generated generated) {
        InterviewAnswerEvaluation evaluation = evaluations.findByIdForUpdate(claim.evaluationId()).orElse(null);

        LocalDateTime now = databaseNow();

        if (isInactive(evaluation, claim, now)) {
            return false;
        }

        AnswerEvaluationPolicy.Result result = AnswerEvaluationPolicy.validate(generated.result());

        evaluation.complete(
                claim.attemptId(),
                result.starScore(),
                result.logicScore(),
                result.jobFitScore(),
                result.strengths(),
                result.improvements(),
                result.improvedAnswer(),
                generated.contextSnapshot(),
                now
        );

        return true;
    }


    public boolean fail(Claim claim, String code, boolean retryable) {
        if (code == null || !code.matches("[A-Z][A-Z0-9_]{0,49}")) {
            throw new IllegalArgumentException("실패 코드 형식이 올바르지 않습니다.");
        }

        InterviewAnswerEvaluation evaluation = evaluations.findByIdForUpdate(claim.evaluationId()).orElse(null);

        LocalDateTime now = databaseNow();

        if (isInactive(evaluation, claim, now)) {
            return false;
        }

        if (retryable && evaluation.getAttemptCount() < InterviewAnswerEvaluation.MAX_ATTEMPTS) {
            evaluation.retry(
                    claim.attemptId(),
                    code,
                    now.plus(AnswerEvaluationPolicy.retryDelay(
                            evaluation.getAttemptCount()
                    )),
                    now
            );

        } else {
            evaluation.fail(claim.attemptId(), code, now);
        }

        return true;
    }


    private boolean isInactive(InterviewAnswerEvaluation evaluation, Claim claim, LocalDateTime now) {
        return evaluation == null
                || evaluation.getStatus() != AnswerEvaluationStatus.PROCESSING
                || !Objects.equals(evaluation.getAttemptId(), claim.attemptId())
                || evaluation.getLeaseExpiresAt() == null
                || !evaluation.getLeaseExpiresAt().isAfter(now);
    }


    private LocalDateTime databaseNow() {
        return Objects.requireNonNull(
                jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)", LocalDateTime.class),
                "DB 현재 시각은 필수입니다."
        );
    }


    public record Claim(
            long evaluationId,
            String attemptId,
            InterviewGenerationPolicy.Mode mode,
            String model,
            String pipelineVersion,
            AnswerEvaluationGenerator.Input input
    ) {

    }
}
