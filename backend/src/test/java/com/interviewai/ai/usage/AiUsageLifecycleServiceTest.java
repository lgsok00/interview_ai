package com.interviewai.ai.usage;

import com.interviewai.global.error.CatalogException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiUsageLifecycleServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AiUsageRepository repository = mock(AiUsageRepository.class);
    private AiUsageLifecycleService service;

    @BeforeEach
    void setUp() {
        service = service(true);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    }

    @AfterEach
    void clearFlags() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void lockRequiresWritableTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        assertThatThrownBy(service::lockExecution).isInstanceOf(IllegalStateException.class);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        assertThatThrownBy(service::lockExecution).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(repository, jdbc);
    }

    @Test
    void globalLockFailureIsMappedToUsageUnavailable() {
        var cause = new DataAccessResourceFailureException("DB unavailable");
        doThrow(cause).when(repository).lockGlobalState();
        assertThatThrownBy(service::lockExecution).isInstanceOfSatisfying(AiUsageException.class, failure -> {
            assertThat(failure.getCode()).isEqualTo("AI_USAGE_UNAVAILABLE");
            assertThat(failure.getCause()).isSameAs(cause);
        });
    }

    @Test
    void disabledLifecycleDoesNotAccessUsageTables() {
        service = service(false);
        service.lockExecution();
        service.bindAttempt(AiUsageFeature.INITIAL_QUESTIONS, 1, null, null);
        service.retainPending(AiUsageFeature.ANSWER_EVALUATION, 1, null);
        service.finishAsync(AiUsageFeature.COVER_LETTER_DRAFT, 1, null);
        service.requireFollowUp(null, 1, 42);
        service.finishFollowUp(null, 1, 42);
        service.cancelFollowUp(null);
        service.cancelUser(1);
        assertThat(service.recoverReservations()).isZero();
        verifyNoInteractions(repository, jdbc);
    }

    @Test
    void missingFollowUpTokenIsRejectedWithoutDatabaseAccess() {
        assertExpired(() -> service.requireFollowUp(null, 1, 42));
        assertExpired(() -> service.finishFollowUp(null, 1, 42));
        verifyNoInteractions(repository, jdbc);
    }

    @Test
    void nonMatchingOrExpiredFollowUpCannotFinish() {
        UUID token = UUID.randomUUID();
        assertExpired(() -> service.requireFollowUp(token, 1, 42));
        assertExpired(() -> service.finishFollowUp(token, 1, 42));
    }

    @Test
    void followUpTokenMustMatchBothOwnerAndParent() {
        UUID token = UUID.randomUUID();
        when(jdbc.queryForList(anyString(), eq(String.class), eq(token.toString()), eq(token.toString()), eq(1L), eq(42L)))
                .thenReturn(List.of(token.toString()));
        assertThatCode(() -> service.requireFollowUp(token, 1, 42)).doesNotThrowAnyException();
        assertExpired(() -> service.requireFollowUp(token, 2, 42));
        assertExpired(() -> service.requireFollowUp(token, 1, 43));
    }

    @Test
    void asyncCompletionWithDifferentActiveAttemptIsRejected() {
        when(repository.hasActiveReservation(AiUsageFeature.ANSWER_EVALUATION, 42)).thenReturn(true);
        assertThatThrownBy(() -> service.finishAsync(AiUsageFeature.ANSWER_EVALUATION, 42, UUID.randomUUID().toString()))
                .isInstanceOfSatisfying(AiUsageException.class,
                        failure -> assertThat(failure.getCode()).isEqualTo("AI_USAGE_UNAVAILABLE"));
    }

    @Test
    void legacyUnreservedWorkCanFinishWithoutCreatingUsage() {
        assertThatCode(() -> service.finishAsync(AiUsageFeature.INITIAL_QUESTIONS, 42, UUID.randomUUID().toString()))
                .doesNotThrowAnyException();
        verify(repository).hasActiveReservation(AiUsageFeature.INITIAL_QUESTIONS, 42);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void jdbcFailureDuringBindingIsMappedToUsageUnavailable() {
        var cause = new DataAccessResourceFailureException("update failed");
        when(jdbc.update(anyString(), any(Object[].class))).thenThrow(cause);
        assertThatThrownBy(() -> service.bindAttempt(AiUsageFeature.INITIAL_QUESTIONS, 42,
                UUID.randomUUID().toString(), LocalDateTime.now().plusMinutes(2)))
                .isInstanceOfSatisfying(AiUsageException.class, failure -> {
                    assertThat(failure.getCode()).isEqualTo("AI_USAGE_UNAVAILABLE");
                    assertThat(failure.getCause()).isSameAs(cause);
                });
    }

    @Test
    void syncAndEmbeddingFeaturesCannotUseAsyncMethods() {
        for (AiUsageFeature feature : List.of(AiUsageFeature.FOLLOW_UP, AiUsageFeature.PERSONAL_EMBEDDING)) {
            assertThatThrownBy(() -> service.finishAsync(feature, 42, "attempt"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(jdbc, repository);
    }

    private void assertExpired(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(CatalogException.class,
                failure -> assertThat(failure.getCode()).isEqualTo("AI_REQUEST_EXPIRED"));
    }

    private AiUsageLifecycleService service(boolean enabled) {
        var properties = new AiUsageProperties(enabled, true, true,
                enabled ? AiUsageConfigurationTest.TEST_KEY : "", 3, 15, 30, 3, 20, 2, 50, 500, 10, 90);
        return new AiUsageLifecycleService(jdbc, repository, properties);
    }
}
