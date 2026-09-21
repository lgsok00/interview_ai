package com.interviewai.interview.evaluation;


import java.time.Duration;
import java.util.Objects;

public final class AnswerEvaluationPolicy {

    public static final String PIPELINE_VERSION = "answer-evaluation-v1";
    public static final int LEASE_SECONDS = 120;
    public static final Duration CALL_TIMEOUT = Duration.ofSeconds(45);


    private AnswerEvaluationPolicy() {

    }


    public static Result validate(Result result) {
        Objects.requireNonNull(result, "평가 결과는 필수입니다.");

        return new Result(
                requireScore(result.starScore(), "starScore"),
                requireScore(result.logicScore(), "logicScore"),
                requireScore(result.jobFitScore(), "jobFitScore"),
                requireText(result.strengths(), "strengths", 20, 3000),
                requireText(result.improvements(), "improvements", 20, 3000),
                requireText(result.improvedAnswer(), "improvedAnswer", 1, 10000)
        );
    }


    public static Duration retryDelay(int attemptCount) {
        return switch (attemptCount) {
            case 1 -> Duration.ofSeconds(10);
            case 2 -> Duration.ofSeconds(30);
            default -> throw new IllegalArgumentException("재시도 지연을 계산할 수 없는 attemptCount입니다: " + attemptCount);
        };
    }


    public static EvaluationException invalidOutput() {
        return new EvaluationException("ANSWER_EVALUATION_INVALID_OUTPUT", true);
    }


    private static int requireScore(int value, String field) {
        if (value < 0 || value > 100) {
            throw invalidOutput();
        }

        return value;
    }


    private static String requireText(String value, String field, int minCodePoints, int maxCodePoints) {
        if (value == null) {
            throw invalidOutput();
        }

        String normalized = value.strip();
        int length = normalized.codePointCount(0, normalized.length());

        if (normalized.isBlank() || length < minCodePoints || length > maxCodePoints || normalized.length() > 10000) {
            throw invalidOutput();
        }

        return normalized;
    }


    public record Result(
            int starScore,
            int logicScore,
            int jobFitScore,
            String strengths,
            String improvements,
            String improvedAnswer
    ) {

    }


    public static final class EvaluationException extends RuntimeException {
        private final String code;
        private final boolean retryable;


        public EvaluationException(String code, boolean retryable) {
            super(code);
            this.code = Objects.requireNonNull(code, "code는 필수입니다.");
            this.retryable = retryable;
        }


        public EvaluationException(String code, boolean retryable, Throwable cause) {
            super(code, cause);
            this.code = Objects.requireNonNull(code, "code는 필수입니다.");
            this.retryable = retryable;
        }


        public String code() {
            return code;
        }


        public boolean retryable() {
            return retryable;
        }
    }
}
