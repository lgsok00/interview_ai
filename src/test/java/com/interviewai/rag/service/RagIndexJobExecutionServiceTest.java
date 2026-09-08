package com.interviewai.rag.service;

import com.interviewai.rag.repository.RagIndexJobExecutionRepository;
import com.interviewai.rag.repository.RagIndexJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagIndexJobExecutionServiceTest {

    @Mock private RagIndexJobExecutionRepository executionRepository;
    @Mock private RagIndexJobRepository jobRepository;
    private RagIndexJobExecutionService service;
    private final UUID attemptId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RagIndexJobExecutionService(executionRepository, jobRepository, 60, 30);
    }

    @ParameterizedTest
    @ValueSource(strings = {"A", "PROCESSING_FAILED", "PROCESSING_INTERRUPTED", "ERROR_123"})
    void acceptsValidFailureCodes(String code) {
        when(executionRepository.fail(1L, attemptId, code, 30)).thenReturn(1);
        assertThat(service.fail(1L, attemptId, code)).isTrue();
    }

    @Test
    void acceptsOneHundredCharacterFailureCode() {
        String code = "A".repeat(100);
        when(executionRepository.fail(1L, attemptId, code, 30)).thenReturn(1);
        assertThat(service.fail(1L, attemptId, code)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"lowercase", "1_ERROR", " ERROR", "ERROR ", "ERROR\n", "ERROR]", "비밀 본문"})
    void rejectsInvalidFailureCodesBeforeWriting(String code) {
        assertThatThrownBy(() -> service.fail(1L, attemptId, code))
                .isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
        verifyNoInteractions(executionRepository, jobRepository);
    }

    @Test
    void rejectsFailureCodeOverOneHundredCharacters() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> service.fail(1L, attemptId, "A".repeat(101)));
        verifyNoInteractions(executionRepository);
    }

    @ParameterizedTest
    @CsvSource({"0,30", "-1,30", "86401,30", "60,0", "60,-1", "60,86401"})
    void rejectsInvalidDurations(int lease, int delay) {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new RagIndexJobExecutionService(executionRepository, jobRepository, lease, delay));
    }

    @Test
    void rejectsInvalidAttemptArgumentsBeforeWriting() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.renew(0, attemptId));
        assertThatIllegalArgumentException().isThrownBy(() -> service.succeed(-1, attemptId));
        assertThatIllegalArgumentException().isThrownBy(() -> service.fail(0, attemptId, "ERROR"));
        assertThatNullPointerException().isThrownBy(() -> service.renew(1, null));
        assertThatNullPointerException().isThrownBy(() -> service.succeed(1, null));
        assertThatNullPointerException().isThrownBy(() -> service.fail(1, null, "ERROR"));
        verifyNoInteractions(executionRepository);
    }
}
