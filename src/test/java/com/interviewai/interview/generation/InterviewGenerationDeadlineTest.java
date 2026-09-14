package com.interviewai.interview.generation;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewGenerationDeadlineTest {
    @Test
    void returnsResultAndPropagatesFailure() throws Exception {
        var deadline = new InterviewGenerationDeadline();
        assertThat(deadline.call(() -> "result", Duration.ofSeconds(2), "TIMEOUT")).isEqualTo("result");
        RuntimeException failure = new RuntimeException("failure");
        assertThatThrownBy(() -> deadline.call(() -> {
            throw failure;
        }, Duration.ofSeconds(2), "TIMEOUT"))
                .isSameAs(failure);
    }

    @Test
    void cancelsTimedOutCallAndBoundsUncooperativeCalls() throws Exception {
        var deadline = new InterviewGenerationDeadline();
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch exited = new CountDownLatch(4);
        try {
            for (int i = 0; i < 4; i++) {
                CountDownLatch started = new CountDownLatch(1);
                try (var caller = java.util.concurrent.Executors.newSingleThreadExecutor()) {
                    var future = caller.submit(() -> assertThatThrownBy(() -> deadline.call(() -> {
                        started.countDown();
                        try {
                            // Simulate an external call which ignores cancellation.
                            boolean done = false;
                            while (!done) {
                                try {
                                    done = release.await(5, TimeUnit.SECONDS);
                                    if (!done) throw new AssertionError("test release timed out");
                                } catch (InterruptedException ignored) {
                                    // Keep permit until the actual external call ends.
                                }
                            }
                            return null;
                        } finally {
                            exited.countDown();
                        }
                    }, Duration.ofMillis(300), "AI_TIMEOUT"))
                            .isInstanceOf(InterviewGenerationPolicy.GenerationException.class)
                            .hasMessage("AI_TIMEOUT"));
                    assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
                    future.get(2, TimeUnit.SECONDS);
                }
            }
            assertThatThrownBy(() -> deadline.call(() -> "unexpected", Duration.ofSeconds(1), "TIMEOUT"))
                    .hasMessage("GENERATION_CAPACITY_EXCEEDED");
        } finally {
            release.countDown();
            assertThat(exited.await(3, TimeUnit.SECONDS)).isTrue();
        }
    }
}
