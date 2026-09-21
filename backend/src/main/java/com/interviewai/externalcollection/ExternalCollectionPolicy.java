package com.interviewai.externalcollection;

public final class ExternalCollectionPolicy {

    public static final int MAX_ATTEMPTS_PER_CYCLE = 3;
    public static final int MAX_MANUAL_RETRIES = 2;
    public static final int MAX_TOTAL_ATTEMPTS = MAX_ATTEMPTS_PER_CYCLE * (MAX_MANUAL_RETRIES + 1);
    public static final long LEASE_SECONDS = 120;


    private ExternalCollectionPolicy() {

    }


    public static int maximumAttemptsForCurrentCycle(int manualRetryCount) {
        if (manualRetryCount < 0 || manualRetryCount > MAX_MANUAL_RETRIES) {
            throw new IllegalArgumentException("수동 재시도 횟수가 올바르지 않습니다.");
        }

        return MAX_ATTEMPTS_PER_CYCLE * (manualRetryCount + 1);
    }
}
