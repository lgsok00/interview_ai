package com.interviewai.externalcollection.entity;

import com.interviewai.externalcollection.enums.ExternalCollectionKind;
import com.interviewai.externalcollection.enums.ExternalCollectionSnapshotStatus;
import com.interviewai.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ExternalCollectionSnapshotTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 18, 11, 0);
    private static final String ATTEMPT_ID = "00000000-0000-0000-0000-000000000001";
    private static final String SHA256 = "a".repeat(64);


    @Test
    @DisplayName("성공한 수집 결과와 검수 후보를 불변 스냅샷으로 만든다")
    void createsSuccessSnapshot() {
        ExternalCollectionSnapshot snapshot = ExternalCollectionSnapshot.success(
                request(),
                1,
                ATTEMPT_ID,
                "https://careers.example.com/jobs/1",
                200,
                "text/html;charset=UTF-8",
                SHA256,
                "백엔드 개발자 채용 공고",
                "{\"title\":\"백엔드 개발자\"}",
                "{\"title\":[0,10]}",
                "external-collection-v1",
                NOW
        );

        assertThat(snapshot.getRequest()).isNotNull();
        assertThat(snapshot.getAttemptNumber()).isEqualTo(1);
        assertThat(snapshot.getAttemptId()).isEqualTo(ATTEMPT_ID);
        assertThat(snapshot.getStatus()).isEqualTo(ExternalCollectionSnapshotStatus.SUCCEEDED);
        assertThat(snapshot.getFinalUrl()).isEqualTo("https://careers.example.com/jobs/1");
        assertThat(snapshot.getHttpStatus()).isEqualTo(200);
        assertThat(snapshot.getContentType()).isEqualTo("text/html;charset=UTF-8");
        assertThat(snapshot.getBodySha256()).isEqualTo(SHA256);
        assertThat(snapshot.getExtractedText()).isEqualTo("백엔드 개발자 채용 공고");
        assertThat(snapshot.getCandidateJson()).contains("백엔드 개발자");
        assertThat(snapshot.getEvidenceJson()).contains("title");
        assertThat(snapshot.getParserVersion()).isEqualTo("external-collection-v1");
        assertThat(snapshot.getFailureCode()).isNull();
        assertThat(snapshot.getCollectedAt()).isEqualTo(NOW);
    }


    @Test
    @DisplayName("실패한 수집 시도는 실패 코드만 가진 스냅샷으로 만든다")
    void createsFailureSnapshot() {
        ExternalCollectionSnapshot snapshot = ExternalCollectionSnapshot.failure(
                request(), 2, ATTEMPT_ID, "FETCH_TIMEOUT", NOW
        );

        assertThat(snapshot.getStatus()).isEqualTo(ExternalCollectionSnapshotStatus.FAILED);
        assertThat(snapshot.getFailureCode()).isEqualTo("FETCH_TIMEOUT");
        assertThat(snapshot.getFinalUrl()).isNull();
        assertThat(snapshot.getHttpStatus()).isNull();
        assertThat(snapshot.getExtractedText()).isNull();
        assertThat(snapshot.getCandidateJson()).isNull();
        assertThat(snapshot.getEvidenceJson()).isNull();
        assertThat(snapshot.getParserVersion()).isNull();
    }


    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    @DisplayName("성공 스냅샷의 필수 수집 값을 거부한다")
    void rejectsMissingSuccessValue(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> ExternalCollectionSnapshot.success(
                request(), 1, ATTEMPT_ID, value, 200, "text/html", SHA256,
                "본문", "{}", "{}", "external-collection-v1", NOW
        ));

        assertThatIllegalArgumentException().isThrownBy(() -> ExternalCollectionSnapshot.success(
                request(), 1, ATTEMPT_ID, "https://example.com", 200, value, SHA256,
                "본문", "{}", "{}", "external-collection-v1", NOW
        ));

        assertThatIllegalArgumentException().isThrownBy(() -> ExternalCollectionSnapshot.success(
                request(), 1, ATTEMPT_ID, "https://example.com", 200, "text/html", SHA256,
                value, "{}", "{}", "external-collection-v1", NOW
        ));
    }


    @Test
    @DisplayName("실패 코드 형식이 잘못된 실패 스냅샷을 거부한다")
    void rejectsInvalidFailureCode() {
        assertThatIllegalArgumentException().isThrownBy(() -> ExternalCollectionSnapshot.failure(
                request(), 1, ATTEMPT_ID, "invalid-code", NOW
        ));
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
