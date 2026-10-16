package com.interviewai.ai.usage;

import com.interviewai.auth.exception.InvalidAccessTokenException;
import com.interviewai.global.error.CatalogException;
import com.interviewai.user.entity.User;
import com.interviewai.user.exception.UserNotFoundException;
import com.interviewai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AiUsageQueryServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 16);
    private static final LocalDateTime NOW = DAY.atTime(10, 0);
    private final UserRepository users = mock(UserRepository.class);
    private final AiUsageQueryRepository repository = mock(AiUsageQueryRepository.class);
    private final AiUsageIdentity identity = mock(AiUsageIdentity.class);
    private AiUsageQueryService service;

    @BeforeEach
    void setUp() {
        configure(true, true);
        when(users.findById(17L)).thenReturn(Optional.of(
                User.createLocalUser("user@example.com", "hash", "사용자")));
        when(repository.currentUtcTime()).thenReturn(NOW);
        when(identity.subjectKey("user@example.com")).thenReturn("subject-key");
        when(repository.findSnapshot(anyString(), any())).thenReturn(snapshot(0, 0));
    }

    @Test
    void returnsFourChatFeaturesWithConfiguredLimitsAndSubjectScope() {
        when(repository.findSnapshot("subject-key", DAY)).thenReturn(
                new AiUsageQueryRepository.Snapshot(Map.of(
                        AiUsageFeature.INITIAL_QUESTIONS, 1,
                        AiUsageFeature.FOLLOW_UP, 4,
                        AiUsageFeature.ANSWER_EVALUATION, 7,
                        AiUsageFeature.COVER_LETTER_DRAFT, 2), 2));

        var result = service.getUsage("17");

        assertThat(result.enabled()).isTrue();
        assertThat(result.chatAdmissionEnabled()).isTrue();
        assertThat(result.timeZone()).isEqualTo("Asia/Seoul");
        assertThat(result.usageDate()).isEqualTo(DAY);
        assertThat(result.resetAt()).isEqualTo(OffsetDateTime.parse("2026-10-17T00:00:00+09:00"));
        assertThat(result.active()).isEqualTo(2L);
        assertThat(result.activeLimit()).isEqualTo(2);
        assertThat(result.features()).containsExactly(
                new AiUsageResponse.FeatureUsage(AiUsageFeature.INITIAL_QUESTIONS, 3, 1, 2),
                new AiUsageResponse.FeatureUsage(AiUsageFeature.FOLLOW_UP, 15, 4, 11),
                new AiUsageResponse.FeatureUsage(AiUsageFeature.ANSWER_EVALUATION, 30, 7, 23),
                new AiUsageResponse.FeatureUsage(AiUsageFeature.COVER_LETTER_DRAFT, 3, 2, 1));
        verify(identity).subjectKey("user@example.com");
        verify(repository).findSnapshot("subject-key", DAY);
    }

    @ParameterizedTest
    @CsvSource({"0,3", "2,1", "3,0", "4,0"})
    void clampsRemainingWithoutHidingAcceptedCount(int used, int remaining) {
        when(repository.findSnapshot(anyString(), any())).thenReturn(snapshot(used, 0));
        var usage = service.getUsage("17").features().getFirst();
        assertThat(usage.used()).isEqualTo(used);
        assertThat(usage.remaining()).isEqualTo(remaining);
    }

    @ParameterizedTest
    @CsvSource({
            "2026-10-16T14:59:59.999999,2026-10-16,2026-10-17T00:00:00+09:00",
            "2026-10-16T15:00:00,2026-10-17,2026-10-18T00:00:00+09:00",
            "2026-12-31T15:00:00,2027-01-01,2027-01-02T00:00:00+09:00"
    })
    void derivesUsageDayAndResetFromDatabaseUtcClock(String utc, String day, String reset) {
        when(repository.currentUtcTime()).thenReturn(LocalDateTime.parse(utc));
        when(repository.findSnapshot(anyString(), any())).thenReturn(snapshot(0, 5));
        var result = service.getUsage("17");
        assertThat(result.usageDate()).isEqualTo(LocalDate.parse(day));
        assertThat(result.resetAt()).isEqualTo(OffsetDateTime.parse(reset));
        assertThat(result.active()).isEqualTo(5L);
        verify(repository).findSnapshot("subject-key", LocalDate.parse(day));
    }

    @Test
    void returnsUsageWhenNewAdmissionsAreStopped() {
        configure(true, false);
        var result = service.getUsage("17");
        assertThat(result.chatAdmissionEnabled()).isFalse();
        assertThat(result.features()).hasSize(4);
    }

    @Test
    void disabledLimitsSkipIdentityAndCountersAndReturnUntrackedState() {
        configure(false, false);
        var result = service.getUsage("17");
        assertThat(result.enabled()).isFalse();
        assertThat(result.chatAdmissionEnabled()).isTrue();
        assertThat(result.active()).isNull();
        assertThat(result.activeLimit()).isNull();
        assertThat(result.features()).isEmpty();
        verifyNoInteractions(identity);
        verify(repository, never()).findSnapshot(anyString(), any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "abc", "0", "-1", "9223372036854775808", " 17 "})
    void rejectsInvalidSubjectBeforeDatabaseAccess(String subject) {
        assertThatThrownBy(() -> service.getUsage(subject)).isInstanceOf(InvalidAccessTokenException.class);
        verifyNoInteractions(users, repository, identity);
    }

    @Test
    void rejectsMissingUserBeforeUsageLookup() {
        when(users.findById(17L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getUsage("17")).isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(repository, identity);
    }

    @Test
    void rejectsSuspendedUserEvenWhenLimitsAreDisabled() {
        configure(false, false);
        var user = User.createLocalUser("user@example.com", "hash", "사용자");
        user.suspend(NOW);
        when(users.findById(17L)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.getUsage("17"))
                .isInstanceOfSatisfying(CatalogException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("USER_SUSPENDED");
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
        verifyNoInteractions(repository, identity);
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "clock", "snapshot"})
    void translatesDatabaseFailuresWithoutReturningZeroUsage(String phase) {
        var failure = new DataAccessResourceFailureException("private-database-detail");
        switch (phase) {
            case "user" -> when(users.findById(17L)).thenThrow(failure);
            case "clock" -> when(repository.currentUtcTime()).thenThrow(failure);
            case "snapshot" -> when(repository.findSnapshot(anyString(), any())).thenThrow(failure);
            default -> throw new AssertionError(phase);
        }
        assertThatThrownBy(() -> service.getUsage("17"))
                .isInstanceOfSatisfying(AiUsageException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("AI_USAGE_UNAVAILABLE");
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(error.getMessage()).doesNotContain("private-database-detail");
                    assertThat(error.getCause()).isSameAs(failure);
                });
    }

    private void configure(boolean enabled, boolean admissionEnabled) {
        var properties = new AiUsageProperties(enabled, admissionEnabled, true,
                enabled ? AiUsageConfigurationTest.TEST_KEY : "",
                3, 15, 30, 3, 20, 2, 50, 500, 10, 90);
        service = new AiUsageQueryService(users, repository, properties, identity);
    }

    private AiUsageQueryRepository.Snapshot snapshot(int initialUsed, long active) {
        return new AiUsageQueryRepository.Snapshot(Map.of(
                AiUsageFeature.INITIAL_QUESTIONS, initialUsed,
                AiUsageFeature.FOLLOW_UP, 0,
                AiUsageFeature.ANSWER_EVALUATION, 0,
                AiUsageFeature.COVER_LETTER_DRAFT, 0), active);
    }
}
