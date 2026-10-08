package com.interviewai.ai.usage;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AiUsageException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Long retryAfterSeconds;


    private AiUsageException(
            HttpStatus status,
            String code,
            String message,
            Long retryAfterSeconds,
            Throwable cause
    ) {
        super(message, cause);
        this.status = status;
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }


    public static AiUsageException dailyLimit(int limit, long retryAfterSeconds) {
        return new AiUsageException(
                HttpStatus.TOO_MANY_REQUESTS,
                "AI_DAILY_LIMIT_EXCEEDED",
                "이 기능의 하루 접수 한도 " + limit + "회를 모두 사용했습니다. 다음 한국 시간 자정 이후 다시 요청해 주세요.",
                retryAfterSeconds,
                null
        );
    }


    public static AiUsageException pendingLimit(int limit) {
        return new AiUsageException(
                HttpStatus.TOO_MANY_REQUESTS,
                "AI_PENDING_LIMIT_EXCEEDED",
                "동시에 대기·실행할 수 있는 AI 작업은 " + limit + "건입니다. 진행 중 작업 완료 후 다시 요청해 주세요.",
                null,
                null
        );
    }


    public static AiUsageException capacity(String message) {
        return new AiUsageException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI_CAPACITY_EXCEEDED",
                message,
                null,
                null
        );
    }


    public static AiUsageException inProgress() {
        return new AiUsageException(
                HttpStatus.CONFLICT,
                "AI_REQUEST_IN_PROGRESS",
                "같은 대상의 AI 작업이 이미 접수되어 있습니다. 작업 상태를 확인해 주세요.",
                null,
                null
        );
    }


    public static AiUsageException unavailable(Throwable cause) {
        return new AiUsageException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI_USAGE_UNAVAILABLE",
                "AI 사용량을 확인하거나 저장할 수 없습니다. 잠시 후 다시 요청해 주세요.",
                null,
                cause
        );
    }
}
