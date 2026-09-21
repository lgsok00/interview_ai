package com.interviewai.externalcollection.entity;

import com.interviewai.externalcollection.enums.ExternalCollectionKind;
import com.interviewai.externalcollection.enums.ExternalCollectionRequestStatus;
import com.interviewai.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ExternalCollectionRequestTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 18, 10, 0);
    private static final UUID FIRST_ATTEMPT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_ATTEMPT = UUID.fromString("00000000-0000-0000-0000-000000000002");


    @Test
    @DisplayName("수집 요청을 대기 상태와 초기 실행 정보로 생성한다")
    void createsPendingRequest() {
        ExternalCollectionRequest request = request();

        assertThat(request.getCollectionKind()).isEqualTo(ExternalCollectionKind.JOB_POSTING);
        assertThat(request.getSourceUrl()).isEqualTo("https://careers.example.com/jobs/1");
        assertThat(request.getNormalizedSourceUrl()).isEqualTo("https://careers.example.com/jobs/1");
        assertThat(request.getRequestedBy()).isNotNull();
        assertThat(request.getStatus()).isEqualTo(ExternalCollectionRequestStatus.PENDING);
        assertThat(request.getAttemptCount()).isZero();
        assertThat(request.getManualRetryCount()).isZero();
        assertThat(request.getAvailableAt()).isEqualTo(NOW);
        assertThat(request.getAttemptId()).isNull();
        assertThat(request.getLeaseExpiresAt()).isNull();
        assertThat(request.getLastErrorCode()).isNull();
        assertThat(request.getCreatedAt()).isEqualTo(NOW);
        assertThat(request.getUpdatedAt()).isEqualTo(NOW);
    }


    @Test
    @DisplayName("대기 요청을 선점하고 유효한 실행만 검수 대기로 완료한다")
    void claimsAndCompletesReviewReady() {
        ExternalCollectionRequest request = request();

        ExternalCollectionRequest.Attempt attempt = request.claim(FIRST_ATTEMPT, NOW);

        assertThat(attempt.number()).isEqualTo(1);
        assertThat(attempt.id()).isEqualTo(FIRST_ATTEMPT.toString());
        assertThat(request.getStatus()).isEqualTo(ExternalCollectionRequestStatus.RUNNING);
        assertThat(request.getAttemptCount()).isEqualTo(1);
        assertThat(request.getAttemptId()).isEqualTo(FIRST_ATTEMPT.toString());
        assertThat(request.getLeaseExpiresAt()).isEqualTo(NOW.plusSeconds(120));
        assertThat(request.getAvailableAt()).isNull();

        request.renewLease(FIRST_ATTEMPT.toString(), NOW.plusSeconds(30));
        assertThat(request.getLeaseExpiresAt()).isEqualTo(NOW.plusSeconds(150));

        request.completeReviewReady(FIRST_ATTEMPT.toString(), NOW.plusSeconds(31));

        assertThat(request.getStatus()).isEqualTo(ExternalCollectionRequestStatus.REVIEW_READY);
        assertThat(request.getAttemptId()).isNull();
        assertThat(request.getLeaseExpiresAt()).isNull();
        assertThat(request.getAvailableAt()).isNull();
        assertThat(request.getLastErrorCode()).isNull();
        assertThat(request.getUpdatedAt()).isEqualTo(NOW.plusSeconds(31));
    }


    @Test
    @DisplayName("lease 만료 시 새 실행이 선점하고 이전 실행의 상태 변경을 거부한다")
    void reclaimsExpiredLeaseAndRejectsOldAttempt() {
        ExternalCollectionRequest request = request();
        request.claim(FIRST_ATTEMPT, NOW);

        LocalDateTime expiry = NOW.plusSeconds(120);
        assertThat(request.canBeClaimed(expiry.minusNanos(1_000))).isFalse();
        assertThat(request.canBeClaimed(expiry)).isTrue();

        ExternalCollectionRequest.Attempt reclaimed = request.claim(SECOND_ATTEMPT, expiry);

        assertThat(reclaimed.number()).isEqualTo(2);
        assertThat(request.getAttemptCount()).isEqualTo(2);
        assertThatIllegalStateException().isThrownBy(() ->
                request.completeReviewReady(FIRST_ATTEMPT.toString(), expiry.plusSeconds(1))
        );
        assertThatIllegalStateException().isThrownBy(() ->
                request.fail(FIRST_ATTEMPT.toString(), "OLD_ATTEMPT", expiry.plusSeconds(1))
        );

        request.completeReviewReady(SECOND_ATTEMPT.toString(), expiry.plusSeconds(1));
        assertThat(request.getStatus()).isEqualTo(ExternalCollectionRequestStatus.REVIEW_READY);
    }


    @Test
    @DisplayName("실패한 요청은 두 번만 수동 재시도한다")
    void boundsManualRetries() {
        ExternalCollectionRequest request = request();

        failAttempt(request, FIRST_ATTEMPT, NOW);
        assertThat(request.getStatus()).isEqualTo(ExternalCollectionRequestStatus.FAILED);
        assertThat(request.getLastErrorCode()).isEqualTo("FETCH_TIMEOUT");

        request.retry(NOW.plusMinutes(1));
        assertThat(request.getStatus()).isEqualTo(ExternalCollectionRequestStatus.PENDING);
        assertThat(request.getManualRetryCount()).isEqualTo(1);
        assertThat(request.getLastErrorCode()).isNull();

        failAttempt(request, SECOND_ATTEMPT, NOW.plusMinutes(1));
        request.retry(NOW.plusMinutes(2));
        assertThat(request.getManualRetryCount()).isEqualTo(2);

        failAttempt(
                request,
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                NOW.plusMinutes(2)
        );

        assertThatIllegalStateException().isThrownBy(() -> request.retry(NOW.plusMinutes(3)));
        assertThat(request.getStatus()).isEqualTo(ExternalCollectionRequestStatus.FAILED);
        assertThat(request.getManualRetryCount()).isEqualTo(2);
    }


    @Test
    @DisplayName("잘못된 실패 코드는 실행 상태를 일부 변경하지 않는다")
    void rejectsInvalidFailureCodeWithoutPartialMutation() {
        ExternalCollectionRequest request = request();
        request.claim(FIRST_ATTEMPT, NOW);

        assertThatIllegalArgumentException().isThrownBy(() ->
                request.fail(FIRST_ATTEMPT.toString(), "invalid-code", NOW.plusSeconds(1))
        );

        assertThat(request.getStatus()).isEqualTo(ExternalCollectionRequestStatus.RUNNING);
        assertThat(request.getAttemptId()).isEqualTo(FIRST_ATTEMPT.toString());
        assertThat(request.getLeaseExpiresAt()).isEqualTo(NOW.plusSeconds(120));
        assertThat(request.getLastErrorCode()).isNull();
    }


    @Test
    @DisplayName("검수 대기 요청만 폐기할 수 있다")
    void rejectsOnlyReviewReadyRequest() {
        ExternalCollectionRequest request = request();

        assertThatIllegalStateException().isThrownBy(() -> request.reject(NOW.plusSeconds(1)));

        request.claim(FIRST_ATTEMPT, NOW);
        request.completeReviewReady(FIRST_ATTEMPT.toString(), NOW.plusSeconds(1));
        request.reject(NOW.plusSeconds(2));

        assertThat(request.getStatus()).isEqualTo(ExternalCollectionRequestStatus.REJECTED);
        assertThat(request.getUpdatedAt()).isEqualTo(NOW.plusSeconds(2));
        assertThatIllegalStateException().isThrownBy(() -> request.reject(NOW.plusSeconds(3)));
    }


    private void failAttempt(ExternalCollectionRequest request, UUID attemptId, LocalDateTime now) {
        request.claim(attemptId, now);
        request.fail(attemptId.toString(), "FETCH_TIMEOUT", now.plusSeconds(1));
    }


    private ExternalCollectionRequest request() {
        return ExternalCollectionRequest.create(
                ExternalCollectionKind.JOB_POSTING,
                "https://careers.example.com/jobs/1",
                "https://careers.example.com/jobs/1",
                User.createLocalUser("admin@example.com", "{bcrypt}encoded", "관리자"),
                NOW
        );
    }
}
