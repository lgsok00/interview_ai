package com.interviewai.coverletter.draft;

import java.time.Duration;

public final class CoverLetterDraftPolicy {

    public static final String PROMPT_TEMPLATE_VERSION = "cover-letter-draft-v1";
    public static final Duration CALL_TIMEOUT = Duration.ofSeconds(45);


    private CoverLetterDraftPolicy() {

    }


    public static Duration retryDelay(int attemptCount) {
        return switch (attemptCount) {
            case 1 -> Duration.ofSeconds(1);
            case 2 -> Duration.ofSeconds(5);
            default -> Duration.ZERO;
        };
    }


    public static DraftException invalidOutput() {
        return new DraftException("AI_INVALID_OUTPUT", true);
    }


    public static final class DraftException extends RuntimeException {

        private final String code;
        private final boolean retryable;


        public DraftException(String code, boolean retryable) {
            super(code);
            this.code = code;
            this.retryable = retryable;
        }


        public DraftException(String code, boolean retryable, Throwable cause) {
            super(code, cause);
            this.code = code;
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
