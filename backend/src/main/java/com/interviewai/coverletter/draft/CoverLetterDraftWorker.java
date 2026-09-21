package com.interviewai.coverletter.draft;

import com.interviewai.interview.generation.InterviewGenerationDeadline;
import com.interviewai.interview.generation.InterviewGenerationPolicy;
import com.openai.errors.OpenAIServiceException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Optional;

@Service
public class CoverLetterDraftWorker {

    private final CoverLetterDraftExecutionService execution;
    private final CoverLetterDraftGenerator generator;
    private final InterviewGenerationDeadline deadline;


    public CoverLetterDraftWorker(
            CoverLetterDraftExecutionService execution,
            CoverLetterDraftGenerator generator,
            InterviewGenerationDeadline deadline
    ) {
        this.execution = execution;
        this.generator = generator;
        this.deadline = deadline;
    }


    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean runOnce() {
        if (Thread.currentThread().isInterrupted()) {
            return false;
        }

        if (execution.recoverOneExhaustedLease()) {
            return true;
        }

        Optional<CoverLetterDraftExecutionService.Claim> candidate = execution.claimNext();

        if (candidate.isEmpty()) {
            return false;
        }

        CoverLetterDraftExecutionService.Claim claim = candidate.get();
        CoverLetterDraft.GenerateDraft generated;

        try {
            generated = deadline.call(
                    () -> generator.generate(claim.input(), claim.model(), claim.promptTemplateVersion()),
                    CoverLetterDraftPolicy.CALL_TIMEOUT,
                    "DRAFT_GENERATION_TIMEOUT"
            );

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            return false;

        } catch (RuntimeException exception) {
            Failure failure = classify(exception);
            execution.fail(claim, failure.code(), failure.retryable());

            return true;
        }

        execution.complete(claim, generated);

        return true;
    }


    private Failure classify(RuntimeException exception) {
        Throwable current = exception;

        for (int depth = 0; current != null && depth < 20; depth++) {
            switch (current) {
                case DataAccessException dataAccessException -> throw dataAccessException;
                case CoverLetterDraftPolicy.DraftException failure -> {
                    return new Failure(failure.code(), failure.retryable());
                }
                case InterviewGenerationPolicy.GenerationException failure -> {
                    return new Failure(failure.code(), failure.retryable());
                }
                case OpenAIServiceException serviceException -> {
                    int status = serviceException.statusCode();

                    if (status == 429) {
                        if ("insufficient_quota".equals(serviceException.code().orElse(""))) {
                            return new Failure("DRAFT_QUOTA_EXHAUSTED", false);
                        }

                        return new Failure("DRAFT_RATE_LIMITED", true);
                    }

                    if (status == 408 || status == 409
                            || status == 500 || status == 502
                            || status == 503 || status == 504
                    ) {
                        return new Failure("DRAFT_UNAVAILABLE", true);
                    }

                    return new Failure("DRAFT_REQUEST_REJECTED", false);
                }

                case IOException ignored -> {
                    return new Failure("DRAFT_NETWORK_ERROR", true);
                }

                default -> {

                }
            }

            current = current.getCause();
        }

        return new Failure("DRAFT_INTERNAL_ERROR", false);
    }


    private record Failure(String code, boolean retryable) {

    }
}
