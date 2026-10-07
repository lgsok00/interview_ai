package com.interviewai.ai.usage;

import com.interviewai.support.ControllerTestSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiUsageErrorResponseTest extends ControllerTestSupport {

    static Stream<Arguments> errors() {
        return Stream.of(
                Arguments.of(AiUsageException.dailyLimit(3, 123),
                        429, "AI_DAILY_LIMIT_EXCEEDED", "123"),
                Arguments.of(AiUsageException.pendingLimit(2),
                        429, "AI_PENDING_LIMIT_EXCEEDED", null),
                Arguments.of(AiUsageException.capacity("전체 접수 한도에 도달했습니다."),
                        503, "AI_CAPACITY_EXCEEDED", null),
                Arguments.of(AiUsageException.inProgress(),
                        409, "AI_REQUEST_IN_PROGRESS", null),
                Arguments.of(AiUsageException.unavailable(new IllegalStateException("private-database-detail")),
                        503, "AI_USAGE_UNAVAILABLE", null)
        );
    }

    @ParameterizedTest
    @MethodSource("errors")
    void returnsStandardErrorBodyAndRetryAfterOnlyForDailyLimit(
            AiUsageException error, int httpStatus, String code, String retryAfter
    ) throws Exception {
        setUpController(new FailureController(error));
        var response = mockMvc.perform(get("/test/ai-usage"))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(error.getMessage()))
                .andExpect(jsonPath("$.errors").isMap())
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.cause").doesNotExist())
                .andExpect(content().string(not(containsString("private-database-detail"))));
        if (retryAfter == null) {
            response.andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER));
        } else {
            response.andExpect(header().string(HttpHeaders.RETRY_AFTER, retryAfter));
        }
    }

    @RestController
    static class FailureController {
        private final AiUsageException error;

        // standalone MockMvc 테스트에서 직접 생성한다. 생성자 인자는 Spring bean이 아니다.
        @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
        FailureController(AiUsageException error) {
            this.error = error;
        }

        @GetMapping("/test/ai-usage")
        public void fail() {
            throw error;
        }
    }
}
