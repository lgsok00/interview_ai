package com.interviewai.ai.usage;

import com.interviewai.global.error.CatalogException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class AiUsageLifecycleService {

    private final JdbcTemplate jdbc;
    private final AiUsageRepository repository;
    private final AiUsageProperties properties;


    public AiUsageLifecycleService(JdbcTemplate jdbc, AiUsageRepository repository, AiUsageProperties properties) {
        this.jdbc = jdbc;
        this.repository = repository;
        this.properties = properties;
    }


    public void lockExecution() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("AI 실행 수명주기는 쓰기 트랜잭션 안에서 처리해야 합니다.");
        }

        if (properties.enabled()) {
            store(() -> {
                repository.lockGlobalState();

                return null;
            });
        }
    }


    public void bindAttempt(AiUsageFeature feature, long resourceId, String attemptId, LocalDateTime leaseExpiresAt) {
        requireAsync(feature);

        if (!properties.enabled()) {
            return;
        }

        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt");

        store(() -> jdbc.update("""
                        UPDATE ai_usage_reservations
                        SET worker_attempt_id = ?,
                            lease_expires_at = ?
                        WHERE feature = ?
                          AND resource_id = ?
                          AND status = 'ACTIVE'
                        """,
                attemptId,
                Timestamp.valueOf(leaseExpiresAt),
                feature.name(),
                resourceId
        ));
    }


    public void retainPending(AiUsageFeature feature, long resourceId, String attemptId) {
        requireAsync(feature);

        if (!properties.enabled()) {
            return;
        }

        Objects.requireNonNull(attemptId, "attemptId");

        store(() -> {
            int changed = jdbc.update("""
                            UPDATE ai_usage_reservations
                            SET worker_attempt_id = NULL,
                                lease_expires_at = NULL
                            WHERE feature = ?
                              AND resource_id = ?
                              AND status = 'ACTIVE'
                              AND (
                                  worker_attempt_id = ?
                                  OR worker_attempt_id IS NULL
                              )
                            """,
                    feature.name(),
                    resourceId,
                    attemptId
            );

            requireChangedOrLegacy(changed, feature, resourceId);

            return null;
        });
    }


    public void finishAsync(AiUsageFeature feature, long resourceId, String attemptId) {
        requireAsync(feature);

        if (!properties.enabled()) {
            return;
        }

        Objects.requireNonNull(attemptId, "attemptId");

        store(() -> {
            int changed = jdbc.update("""
                            UPDATE ai_usage_reservations
                            SET status = 'FINISHED',
                                finished_at = UTC_TIMESTAMP(6),
                                worker_attempt_id = NULL,
                                lease_expires_at = NULL
                            WHERE feature = ?
                              AND resource_id = ?
                              AND status = 'ACTIVE'
                              AND (
                                  worker_attempt_id = ?
                                  OR worker_attempt_id IS NULL
                              )
                            """,
                    feature.name(),
                    resourceId,
                    attemptId
            );

            requireChangedOrLegacy(changed, feature, resourceId);

            return null;
        });
    }


    public void requireFollowUp(UUID reservationId, long userId, long questionId) {
        if (!properties.enabled()) {
            return;
        }

        if (reservationId == null) {
            throw expired();
        }

        store(() -> {
            var rows = jdbc.queryForList("""
                            SELECT id
                            FROM ai_usage_reservations
                            WHERE id = ?
                              AND worker_attempt_id = ?
                              AND user_id = ?
                              AND feature = 'FOLLOW_UP'
                              AND resource_id = ?
                              AND status = 'ACTIVE'
                              AND lease_expires_at > UTC_TIMESTAMP(6)
                            FOR UPDATE
                            """,
                    String.class,
                    reservationId.toString(),
                    reservationId.toString(),
                    userId,
                    questionId
            );

            if (rows.size() != 1) {
                throw expired();
            }

            return null;
        });
    }


    public void finishFollowUp(UUID reservationId, long userId, long questionId) {
        if (!properties.enabled()) {
            return;
        }

        if (reservationId == null) {
            throw expired();
        }

        store(() -> {
            int changed = jdbc.update("""
                            UPDATE ai_usage_reservations
                            SET status = 'FINISHED',
                                finished_at = UTC_TIMESTAMP(6),
                                worker_attempt_id = NULL,
                                lease_expires_at = NULL
                            WHERE id = ?
                              AND worker_attempt_id = ?
                              AND user_id = ?
                              AND feature = 'FOLLOW_UP'
                              AND resource_id = ?
                              AND status = 'ACTIVE'
                              AND lease_expires_at > UTC_TIMESTAMP(6)
                            """,
                    reservationId.toString(),
                    reservationId.toString(),
                    userId,
                    questionId
            );

            if (changed != 1) {
                throw expired();
            }

            return null;
        });
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelFollowUp(UUID reservationId) {
        if (!properties.enabled() || reservationId == null) {
            return;
        }

        lockExecution();

        store(() -> jdbc.update("""
                        UPDATE ai_usage_reservations
                        SET status = CASE
                                WHEN lease_expires_at <= UTC_TIMESTAMP(6)
                                    THEN 'EXPIRED'
                                ELSE 'CANCELLED'
                            END,
                            finished_at = UTC_TIMESTAMP(6),
                            worker_attempt_id = NULL,
                            lease_expires_at = NULL
                        WHERE id = ?
                          AND worker_attempt_id = ?
                          AND feature = 'FOLLOW_UP'
                          AND status = 'ACTIVE'
                        """,
                reservationId.toString(),
                reservationId.toString()
        ));
    }


    public void cancelUser(long userId) {
        if (!properties.enabled()) {
            return;
        }

        store(() -> jdbc.update("""
                        UPDATE ai_usage_reservations
                        SET status = 'CANCELLED',
                            finished_at = UTC_TIMESTAMP(6),
                            worker_attempt_id = NULL,
                            lease_expires_at = NULL
                        WHERE user_id = ?
                          AND status = 'ACTIVE'
                        """,
                userId
        ));
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recoverReservations() {
        if (!properties.enabled()) {
            return 0;
        }

        lockExecution();

        return store(() -> {
            int changed = jdbc.update("""
                    UPDATE ai_usage_reservations
                    SET status = 'CANCELLED',
                        finished_at = UTC_TIMESTAMP(6),
                        worker_attempt_id = NULL,
                        lease_expires_at = NULL
                    WHERE status = 'ACTIVE'
                      AND user_id IS NULL
                    LIMIT 100
                    """);

            changed += jdbc.update("""
                    UPDATE ai_usage_reservations r
                    SET status = 'EXPIRED',
                        finished_at = UTC_TIMESTAMP(6),
                        worker_attempt_id = NULL,
                        lease_expires_at = NULL
                    WHERE r.status = 'ACTIVE'
                      AND r.feature = 'FOLLOW_UP'
                      AND r.lease_expires_at <= UTC_TIMESTAMP(6)
                    LIMIT 100
                    """);

            changed += recoverAsync(
                    AiUsageFeature.INITIAL_QUESTIONS,
                    """
                            SELECT 1
                            FROM interview_generation_jobs job
                            WHERE job.session_id = r.resource_id
                            """,
                    "job.status IN ('PENDING', 'RUNNING')"
            );

            changed += recoverAsync(
                    AiUsageFeature.ANSWER_EVALUATION,
                    """
                            SELECT 1
                            FROM interview_answer_evaluations job
                            WHERE job.answer_id = r.resource_id
                            """,
                    "job.status IN ('PENDING', 'PROCESSING')"
            );

            changed += recoverAsync(
                    AiUsageFeature.COVER_LETTER_DRAFT,
                    """
                            SELECT 1
                            FROM cover_letter_drafts job
                            WHERE job.id = r.resource_id
                            """,
                    "job.status IN ('PENDING', 'RUNNING')"
            );

            changed += jdbc.update("""
                    UPDATE ai_usage_reservations r
                    SET status = 'CANCELLED',
                        finished_at = UTC_TIMESTAMP(6),
                        worker_attempt_id = NULL,
                        lease_expires_at = NULL
                    WHERE r.status = 'ACTIVE'
                      AND r.feature = 'FOLLOW_UP'
                      AND NOT EXISTS(
                          SELECT 1
                          FROM interview_questions question
                          WHERE question.id = r.resource_id
                      )
                    LIMIT 100
                    """);

            return changed;
        });
    }


    private int recoverAsync(AiUsageFeature feature, String sourceQuery, String activeCondition) {
        // SQL 조각은 이 클래스에 정의된 고정 문자열만 전달한다.
        String sql = """
                UPDATE ai_usage_reservations r
                SET status = CASE
                        WHEN EXISTS (%s) THEN 'FINISHED'
                        ELSE 'CANCELLED'
                    END,
                    finished_at = UTC_TIMESTAMP(6),
                    worker_attempt_id = NULL,
                    lease_expires_at = NULL
                WHERE r.status = 'ACTIVE'
                  AND r.feature = ?
                  AND NOT EXISTS (%s AND %s)
                LIMIT 100
                """.formatted(sourceQuery, sourceQuery, activeCondition);

        return jdbc.update(sql, feature.name());
    }


    private void requireChangedOrLegacy(int changed, AiUsageFeature feature, long resourceId) {
        if (changed == 0 && repository.hasActiveReservation(feature, resourceId)) {
            throw AiUsageException.unavailable(new IllegalStateException("AI 예약과 작업 attempt가 일치하지 않습니다."));
        }
    }


    private void requireAsync(AiUsageFeature feature) {
        Objects.requireNonNull(feature, "feature");

        if (!feature.isChat() || feature == AiUsageFeature.FOLLOW_UP) {
            throw new IllegalArgumentException("비동기 Chat 기능만 전달할 수 있습니다.");
        }
    }


    private CatalogException expired() {
        return new CatalogException(
                HttpStatus.CONFLICT,
                "AI_REQUEST_EXPIRED",
                "꼬리 질문 실행 예약이 만료되었거나 종료되었습니다. 질문 목록을 확인한 뒤 다시 요청해 주세요."
        );
    }


    private <T> T store(Supplier<T> operation) {
        try {
            return operation.get();

        } catch (DataAccessException exception) {
            throw AiUsageException.unavailable(exception);
        }
    }
}
