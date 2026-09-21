package com.interviewai.interview.generation;

import com.openai.errors.OpenAIServiceException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

@Service
public class InterviewGenerationWorker {

    private static final Logger log = LoggerFactory.getLogger(InterviewGenerationWorker.class);

    private final InterviewGenerationExecutionService execution;
    private final InterviewGenerationInputService inputs;
    private final InterviewGenerationDeadline deadline;
    private final ObjectProvider<InterviewChatQuestionGenerator> generators;


    public InterviewGenerationWorker(
            InterviewGenerationExecutionService execution,
            InterviewGenerationInputService inputs,
            InterviewGenerationDeadline deadline,
            ObjectProvider<InterviewChatQuestionGenerator> generators
    ) {
        this.execution = execution;
        this.inputs = inputs;
        this.deadline = deadline;
        this.generators = generators;
    }


    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void runOnce() {
        Optional<InterviewGenerationExecutionService.Claim> candidate = execution.claimNext();

        if (candidate.isEmpty()) {
            return;
        }

        InterviewGenerationExecutionService.Claim claim = candidate.get();

        if (!InterviewGenerationPolicy.VERSION.equals(claim.version())) {
            execution.fail(claim, "UNSUPPORTED_GENERATION_VERSION", false);
            return;
        }

        if (claim.mode() == InterviewGenerationPolicy.Mode.FALLBACK_ONLY) {
            complete(claim, InterviewGenerationPolicy.fallback("FALLBACK_ONLY_MODE"));
            return;
        }

        if (claim.recoveryOnly()) {
            complete(claim, InterviewGenerationPolicy.fallback("GENERATION_LEASE_EXHAUSTED"));
            return;
        }

        InterviewGenerationPolicy.Batch batch;
        String stage = "RAG";

        try {
            InterviewGenerationInputService.Input input = deadline.call(
                    () -> inputs.load(claim.sessionId()),
                    Duration.ofSeconds(15),
                    "RAG_TIMEOUT"
            );

            if (!execution.renew(claim)) {
                return;
            }

            if (input.context().isEmpty()) {
                batch = InterviewGenerationPolicy.fallback("RAG_EMPTY");

            } else {
                stage = "AI";

                InterviewChatQuestionGenerator generator = generators.getIfAvailable();

                if (generator == null) {
                    throw new InterviewGenerationPolicy.GenerationException("AI_NOT_CONFIGURED", false);
                }

                batch = deadline.call(
                        () -> generator.generate(input, claim.model()),
                        Duration.ofSeconds(45),
                        "AI_TIMEOUT"
                );
            }

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return;

        } catch (DataAccessException exception) {
            throw exception;

        } catch (RuntimeException exception) {
            Failure failure = classify(exception, stage);

            if (failure.retryable() && claim.attemptCount() >= InterviewGenerationPolicy.MAX_ATTEMPTS) {
                complete(claim, InterviewGenerationPolicy.fallback(failure.code()));

            } else {
                execution.fail(claim, failure.code(), failure.retryable());
            }

            return;
        }

        complete(claim, batch);
    }


    private void complete(InterviewGenerationExecutionService.Claim claim, InterviewGenerationPolicy.Batch batch) {
        if (!execution.complete(claim, batch)) {
            log.debug("질문 생성 결과 반영 생략: sessionId={}, attemptId={}", claim.sessionId(), claim.attemptId());
        }
    }


    private Failure classify(RuntimeException exception, String stage) {
        Throwable current = exception;

        for (int depth = 0; current != null && depth < 20; depth++) {
            if (current instanceof IOException) {
                return new Failure(stage + "_NETWORK_ERROR", true);
            }

            switch (current) {
                case DataAccessException dataAccessException -> throw dataAccessException;

                case InterviewGenerationPolicy.GenerationException failure -> {
                    return new Failure(failure.code(), failure.retryable());
                }

                case OpenAIServiceException serviceException -> {
                    int status = serviceException.statusCode();

                    if (status == 429) {
                        if ("insufficient_quota".equals(serviceException.code().orElse(""))) {
                            return new Failure(stage + "_QUOTA_EXHAUSTED", false);
                        }

                        return new Failure(stage + "_RATE_LIMITED", true);
                    }

                    if (status == 408 || status == 409
                            || status == 500 || status == 502
                            || status == 503 || status == 504
                    ) {
                        return new Failure(stage + "_UNAVAILABLE", true);
                    }

                    return new Failure(stage + "_REQUEST_REJECTED", false);
                }

                case StatusRuntimeException grpcException -> {
                    Status.Code code = grpcException.getStatus().getCode();

                    boolean retryable = code == Status.Code.UNAVAILABLE
                            || code == Status.Code.DEADLINE_EXCEEDED
                            || code == Status.Code.RESOURCE_EXHAUSTED;

                    return new Failure(stage + "_RPC_FAILED", retryable);
                }

                default -> {
                }
            }

            current = current.getCause();
        }

        return new Failure(stage + "_INTERNAL_ERROR", false);
    }


    private record Failure(String code, boolean retryable) {

    }
}
