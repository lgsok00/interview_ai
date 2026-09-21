package com.interviewai.interview.generation;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class InterviewGenerationDeadline {

    private final Semaphore permits = new Semaphore(4);


    public <T> T call(Callable<T> action, Duration timeout, String timeoutCode) throws InterruptedException {
        if (!permits.tryAcquire()) {
            throw new InterviewGenerationPolicy.GenerationException("GENERATION_CAPACITY_EXCEEDED", true);
        }

        FutureTask<T> task = new FutureTask<>(action);

        try {
            Thread.ofVirtual()
                    .name("interview-generation-call")
                    .start(() -> {
                        try {
                            task.run();

                        } finally {
                            permits.release();
                        }
                    });

        } catch (RuntimeException | Error exception) {
            permits.release();
            throw exception;
        }

        try {
            return task.get(timeout.toMillis(), TimeUnit.MILLISECONDS);

        } catch (TimeoutException exception) {
            task.cancel(true);
            throw new InterviewGenerationPolicy.GenerationException(timeoutCode, true);

        } catch (InterruptedException exception) {
            task.cancel(true);
            throw exception;

        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();

            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }

            if (cause instanceof Error error) {
                throw error;
            }

            if (cause instanceof InterruptedException interruptedException) {
                throw interruptedException;
            }

            throw new IllegalStateException("질문 생성 외부 호출에 실패했습니다.", cause);
        }
    }
}
