package com.interviewai.ai.usage;

import com.interviewai.global.error.CatalogException;
import com.interviewai.user.exception.UserNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiUsageAdmissionServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 10, 0);
    private static final LocalDate DAY = NOW.toLocalDate();
    private final AiUsageRepository repository = mock(AiUsageRepository.class);
    private AiUsageProperties properties;
    private AiUsageAdmissionService service;
    private String subject;

    @BeforeEach
    void setUp() {
        configure(true, true);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        when(repository.lockUser(1L)).thenReturn(Optional.of(
                new AiUsageRepository.LockedUser("  User@Example.COM  ", "ACTIVE")));
        when(repository.currentUtcTime()).thenReturn(NOW);
        when(repository.findActiveSubjectsLocked()).thenReturn(List.of());
    }

    @AfterEach
    void clearTransactionFlags() {
        TransactionSynchronizationManager.clear();
    }

    @ParameterizedTest
    @EnumSource(value = AiUsageFeature.class, names = "PERSONAL_EMBEDDING", mode = EnumSource.Mode.EXCLUDE)
    void reservesEachChatFeatureAndChargesOnce(AiUsageFeature feature) {
        var order = inOrder(repository);
        var result = service.admitChat(1, feature, () -> {
            order.verify(repository).lockUser(1);
            order.verify(repository).lockGlobalState();
            order.verify(repository).currentUtcTime();
            order.verify(repository).lockSubject(subject, NOW);
            return AiUsageAdmissionService.Registration.created(42, "new-job");
        });

        assertThat(result.value()).isEqualTo("new-job");
        assertThat(result.reservationId()).isNotNull();
        verify(repository).incrementGlobalDaily(DAY);
        verify(repository).incrementSubjectDaily(subject, DAY, feature);
        verify(repository).insertReservation(result.reservationId(), subject, 1, feature, 42, NOW,
                feature == AiUsageFeature.FOLLOW_UP ? NOW.plusSeconds(90) : null);
    }

    @Test
    void rejectsMissingTransactionBeforeExecutingCallback() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        assertThatThrownBy(() -> create(AiUsageFeature.INITIAL_QUESTIONS))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsReadOnlyTransactionBeforeExecutingCallback() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        assertThatThrownBy(() -> create(AiUsageFeature.INITIAL_QUESTIONS))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsInvalidUserId(long userId) {
        assertThatThrownBy(() -> service.admitChat(userId, AiUsageFeature.FOLLOW_UP,
                () -> AiUsageAdmissionService.Registration.created(42, "job")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsMissingFeatureOrCallbackAndEmbedding() {
        assertThatThrownBy(() -> service.admitChat(1, null,
                () -> AiUsageAdmissionService.Registration.existing("result")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.admitChat(1, AiUsageFeature.FOLLOW_UP, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> create(AiUsageFeature.PERSONAL_EMBEDDING))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsInvalidNewResourceId(long resourceId) {
        assertThatThrownBy(() -> AiUsageAdmissionService.Registration.created(resourceId, "job"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullCallbackResult() {
        assertThatThrownBy(() -> service.admitChat(1, AiUsageFeature.FOLLOW_UP, () -> null))
                .isInstanceOf(NullPointerException.class);
        assertNoCharge();
    }

    @Test
    void explicitlyDisabledLimitsSkipAllUsageStorage() {
        configure(false, false);
        var result = create(AiUsageFeature.INITIAL_QUESTIONS);
        assertThat(result.value()).isEqualTo("job");
        assertThat(result.reservationId()).isNull();
        verifyNoInteractions(repository);
    }

    @Test
    void existingResultRemainsAvailableWhenNewAdmissionsAreStopped() {
        configure(true, false);
        var result = service.admitChat(1, AiUsageFeature.FOLLOW_UP,
                () -> AiUsageAdmissionService.Registration.existing("saved-result"));
        assertThat(result.value()).isEqualTo("saved-result");
        assertThat(result.reservationId()).isNull();
        verify(repository, never()).lockGlobalDaily(any());
        verify(repository, never()).findActiveSubjectsLocked();
        assertNoCharge();
    }

    @Test
    void stoppedAdmissionRejectsNewWorkWithoutCharge() {
        configure(true, false);
        assertError("AI_CAPACITY_EXCEEDED", () -> create(AiUsageFeature.INITIAL_QUESTIONS));
        verify(repository, never()).hasActiveReservation(any(), anyLong());
        assertNoCharge();
    }

    @Test
    void missingUserDoesNotExecuteDomainWork() {
        when(repository.lockUser(1)).thenReturn(Optional.empty());
        AtomicBoolean called = new AtomicBoolean();
        assertThatThrownBy(() -> service.admitChat(1, AiUsageFeature.FOLLOW_UP, () -> {
            called.set(true);
            return AiUsageAdmissionService.Registration.created(42, "job");
        })).isInstanceOf(UserNotFoundException.class);
        assertThat(called).isFalse();
        verify(repository, never()).lockGlobalState();
    }

    @Test
    void suspendedUserDoesNotLockGlobalState() {
        when(repository.lockUser(1)).thenReturn(Optional.of(
                new AiUsageRepository.LockedUser("user@example.com", "SUSPENDED")));
        CatalogException error = catchThrowableOfType(CatalogException.class,
                () -> create(AiUsageFeature.INITIAL_QUESTIONS));
        assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(error.getCode()).isEqualTo("USER_SUSPENDED");
        verify(repository, never()).lockGlobalState();
    }

    @Test
    void domainFailureIsPropagatedWithoutBeingRelabeledAsUsageFailure() {
        var failure = new DataAccessResourceFailureException("domain failure");
        assertThatThrownBy(() -> service.admitChat(1, AiUsageFeature.FOLLOW_UP, () -> {
            throw failure;
        })).isSameAs(failure);
        assertNoCharge();
    }

    @Test
    void duplicateActiveResourceIsRejectedBeforeCountersAreRead() {
        when(repository.hasActiveReservation(AiUsageFeature.FOLLOW_UP, 42)).thenReturn(true);
        assertError("AI_REQUEST_IN_PROGRESS", () -> create(AiUsageFeature.FOLLOW_UP));
        verify(repository, never()).lockGlobalDaily(any());
        assertNoCharge();
    }

    @ParameterizedTest
    @EnumSource(value = AiUsageFeature.class, names = "PERSONAL_EMBEDDING", mode = EnumSource.Mode.EXCLUDE)
    void enforcesConfiguredDailyLimitForEachFeature(AiUsageFeature feature) {
        when(repository.lockSubjectDaily(subject, DAY, feature)).thenReturn(properties.dailyLimit(feature));
        var error = captureError("AI_DAILY_LIMIT_EXCEEDED", () -> create(feature));
        assertThat(error.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(error.getRetryAfterSeconds()).isEqualTo(18000L);
        assertNoCharge();
    }

    @ParameterizedTest
    @CsvSource({
            "2026-10-07T14:59:59.500000, 1",
            "2026-10-07T00:00:00, 54000",
            "2026-10-07T15:00:00, 86400"
    })
    void computesRetryAfterFromKstMidnightAndRoundsUp(String utc, long expectedSeconds) {
        when(repository.currentUtcTime()).thenReturn(LocalDateTime.parse(utc));
        when(repository.lockSubjectDaily(eq(subject), any(), eq(AiUsageFeature.INITIAL_QUESTIONS)))
                .thenReturn(3);
        var error = captureError("AI_DAILY_LIMIT_EXCEEDED",
                () -> create(AiUsageFeature.INITIAL_QUESTIONS));
        assertThat(error.getRetryAfterSeconds()).isEqualTo(expectedSeconds);
    }

    @Test
    void admissionDayUsesTimeAfterDomainPreparationCrossesMidnight() {
        var before = LocalDateTime.parse("2026-10-07T14:59:59.999999");
        var after = LocalDateTime.parse("2026-10-07T15:00:00");
        when(repository.currentUtcTime()).thenReturn(before, after);
        create(AiUsageFeature.INITIAL_QUESTIONS);
        verify(repository).incrementGlobalDaily(LocalDate.of(2026, 10, 8));
        verify(repository).incrementSubjectDaily(subject, LocalDate.of(2026, 10, 8),
                AiUsageFeature.INITIAL_QUESTIONS);
    }

    @Test
    void userSlotsAreSharedAcrossChatFeatures() {
        when(repository.findActiveSubjectsLocked()).thenReturn(List.of(subject, subject, "other"));
        assertError("AI_PENDING_LIMIT_EXCEEDED", () -> create(AiUsageFeature.COVER_LETTER_DRAFT));
        assertNoCharge();
    }

    @Test
    void globalDailyLimitIsEnforcedEvenWhenUserHasCapacity() {
        when(repository.lockGlobalDaily(DAY)).thenReturn(500);
        assertError("AI_CAPACITY_EXCEEDED", () -> create(AiUsageFeature.INITIAL_QUESTIONS));
        assertNoCharge();
    }

    @Test
    void globalSlotsAreEnforcedEvenWhenUserHasCapacity() {
        when(repository.findActiveSubjectsLocked()).thenReturn(java.util.Collections.nCopies(50, "other"));
        assertError("AI_CAPACITY_EXCEEDED", () -> create(AiUsageFeature.INITIAL_QUESTIONS));
        assertNoCharge();
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "globalLock", "clock", "subject", "duplicate",
            "globalDaily", "subjectDaily", "active", "incrementGlobal", "incrementSubject", "reservation"})
    void usageStorageFailureIsReportedAsUnavailable(String stage) {
        var failure = new DataAccessResourceFailureException("storage failure");
        switch (stage) {
            case "user" -> when(repository.lockUser(1)).thenThrow(failure);
            case "globalLock" -> doThrow(failure).when(repository).lockGlobalState();
            case "clock" -> when(repository.currentUtcTime()).thenThrow(failure);
            case "subject" -> doThrow(failure).when(repository).lockSubject(subject, NOW);
            case "duplicate" -> when(repository.hasActiveReservation(AiUsageFeature.INITIAL_QUESTIONS, 42))
                    .thenThrow(failure);
            case "globalDaily" -> when(repository.lockGlobalDaily(DAY)).thenThrow(failure);
            case "subjectDaily" -> when(repository.lockSubjectDaily(subject, DAY, AiUsageFeature.INITIAL_QUESTIONS))
                    .thenThrow(failure);
            case "active" -> when(repository.findActiveSubjectsLocked()).thenThrow(failure);
            case "incrementGlobal" -> doThrow(failure).when(repository).incrementGlobalDaily(DAY);
            case "incrementSubject" -> doThrow(failure).when(repository)
                    .incrementSubjectDaily(subject, DAY, AiUsageFeature.INITIAL_QUESTIONS);
            case "reservation" -> doThrow(failure).when(repository).insertReservation(
                    any(UUID.class), eq(subject), eq(1L), eq(AiUsageFeature.INITIAL_QUESTIONS),
                    eq(42L), eq(NOW), isNull());
            default -> throw new IllegalArgumentException(stage);
        }
        var error = captureError("AI_USAGE_UNAVAILABLE", () -> create(AiUsageFeature.INITIAL_QUESTIONS));
        assertThat(error.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(error.getCause()).isSameAs(failure);
    }

    private void configure(boolean enabled, boolean admissionEnabled) {
        properties = new AiUsageProperties(enabled, admissionEnabled, true,
                enabled ? AiUsageConfigurationTest.TEST_KEY : "",
                3, 15, 30, 3, 20, 2, 50, 500, 10, 90);
        var identity = new AiUsageIdentity(properties);
        subject = enabled ? identity.subjectKey("user@example.com") : null;
        service = new AiUsageAdmissionService(repository, properties, identity);
    }

    @Test
    void scopeLocksBeforeCallbackAndDoesNotChargeOrReserve() {
        var order = inOrder(repository);
        assertThat(service.inChatScope(1, () -> {
            order.verify(repository).lockUser(1);
            order.verify(repository).lockGlobalState();
            order.verify(repository).currentUtcTime();
            order.verify(repository).lockSubject(subject, NOW);
            return "saved";
        })).isEqualTo("saved");
        verify(repository, never()).incrementGlobalDaily(any());
        verify(repository, never()).findActiveSubjectsLocked();
        verify(repository, never()).hasActiveReservation(any(), anyLong());
    }

    @Test
    void disabledScopeDoesNotTouchUsageStore() {
        configure(false, true);
        assertThat(service.inChatScope(1, () -> "saved")).isEqualTo("saved");
        verifyNoInteractions(repository);
    }

    @Test
    void scopeRejectsReadOnlyAndInvalidInputBeforeCallback() {
        var invoked = new AtomicBoolean();
        assertThatThrownBy(() -> service.inChatScope(0, () -> invoked.getAndSet(true)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.inChatScope(1, null)).isInstanceOf(NullPointerException.class);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        assertThatThrownBy(() -> service.inChatScope(1, () -> invoked.getAndSet(true)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(invoked).isFalse();
        verifyNoInteractions(repository);
    }

    private AiUsageAdmissionService.Admission<String> create(AiUsageFeature feature) {
        return service.admitChat(1, feature,
                () -> AiUsageAdmissionService.Registration.created(42, "job"));
    }

    private void assertError(String code, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOf(AiUsageException.class)
                .satisfies(error -> assertThat(((AiUsageException) error).getCode()).isEqualTo(code));
    }

    private AiUsageException captureError(String code, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        var error = catchThrowableOfType(AiUsageException.class, action);
        assertThat(error.getCode()).isEqualTo(code);
        return error;
    }

    private void assertNoCharge() {
        verify(repository, never()).incrementGlobalDaily(any());
        verify(repository, never()).incrementSubjectDaily(anyString(), any(), any());
        verify(repository, never()).insertReservation(any(), anyString(), anyLong(), any(), anyLong(), any(), any());
    }
}
