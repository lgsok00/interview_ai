package com.interviewai.ai.usage;

import com.interviewai.global.error.CatalogException;
import com.interviewai.user.exception.UserNotFoundException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class AiUsageAdmissionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AiUsageRepository repository;
    private final AiUsageProperties properties;
    private final AiUsageIdentity identity;


    public AiUsageAdmissionService(AiUsageRepository repository, AiUsageProperties properties, AiUsageIdentity identity) {
        this.repository = repository;
        this.properties = properties;
        this.identity = identity;
    }


    @Transactional(propagation = Propagation.MANDATORY)
    public <T> Admission<T> admitChat(long userId, AiUsageFeature feature, Supplier<Registration<T>> operation) {
        requireWritableTransaction();

        if (userId <= 0) {
            throw new IllegalArgumentException("사용자 ID는 양수여야 합니다.");
        }

        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(operation, "operation");

        if (!feature.isChat()) {
            throw new IllegalArgumentException("Chat 접수 서비스에는 Chat 기능만 전달할 수 있습니다.");
        }

        if (!properties.enabled()) {
            Registration<T> registration = Objects.requireNonNull(operation.get(), "registration");

            return new Admission<>(registration.value(), null);
        }

        LockedScope scope = lockScope(userId);

        Registration<T> registration = Objects.requireNonNull(operation.get(), "registration");

        if (registration.resourceId() == null) {
            return new Admission<>(registration.value(), null);
        }

        UUID reservationId = reserve(scope, userId, feature, registration.resourceId());

        return new Admission<>(registration.value(), reservationId);
    }


    @Transactional(propagation = Propagation.MANDATORY)
    public <T> T inChatScope(long userId, Supplier<T> operation) {
        requireWritableTransaction();

        if (userId <= 0) {
            throw new IllegalArgumentException("사용자 ID는 양수여야 합니다.");
        }

        Objects.requireNonNull(operation, "operation");

        if (properties.enabled()) {
            lockScope(userId);
        }

        return operation.get();
    }


    private LockedScope lockScope(long userId) {
        try {
            AiUsageRepository.LockedUser user = repository.lockUser(userId)
                    .orElseThrow(UserNotFoundException::new);

            if (!"ACTIVE".equals(user.status())) {
                throw new CatalogException(
                        HttpStatus.FORBIDDEN,
                        "USER_SUSPENDED",
                        "정지된 사용자 계정입니다."
                );
            }

            repository.lockGlobalState();

            String subjectKey = identity.subjectKey(user.email());
            LocalDateTime now = repository.currentUtcTime();

            repository.lockSubject(subjectKey, now);

            return new LockedScope(subjectKey);

        } catch (DataAccessException exception) {
            throw AiUsageException.unavailable(exception);
        }
    }


    private UUID reserve(LockedScope scope, long userId, AiUsageFeature feature, long resourceId) {
        try {
            if (!properties.chatAdmissionEnabled()) {
                throw AiUsageException.capacity("새 AI 작업 접수가 일시 중단되었습니다. 접수 재개 후 다시 요청해 주세요.");
            }

            if (feature == AiUsageFeature.FOLLOW_UP) {
                repository.expireFollowUpReservation(resourceId);
            }

            if (repository.hasActiveReservation(feature, resourceId)) {
                throw AiUsageException.inProgress();
            }

            LocalDateTime now = repository.currentUtcTime();
            ZonedDateTime kstNow = now.atOffset(ZoneOffset.UTC).atZoneSameInstant(KST);
            LocalDate usageDate = kstNow.toLocalDate();

            int globalUsed = repository.lockGlobalDaily(usageDate);
            int subjectUsed = repository.lockSubjectDaily(scope.subjectKey(), usageDate, feature);

            int dailyLimit = properties.dailyLimit(feature);

            if (subjectUsed >= dailyLimit) {
                ZonedDateTime resetAt = usageDate.plusDays(1).atStartOfDay(KST);

                Duration remaining = Duration.between(kstNow.toInstant(), resetAt.toInstant());

                long retryAfterSeconds = remaining.getSeconds() + (remaining.getNano() == 0 ? 0 : 1);

                throw AiUsageException.dailyLimit(dailyLimit, Math.max(1, retryAfterSeconds));
            }

            List<String> activeSubjects = repository.findActiveSubjectsLocked();

            long subjectActive = activeSubjects.stream()
                    .filter(scope.subjectKey()::equals)
                    .count();

            if (subjectActive >= properties.userActiveLimit()) {
                throw AiUsageException.pendingLimit(properties.userActiveLimit());
            }

            if (globalUsed >= properties.globalChatDailyLimit()) {
                throw AiUsageException.capacity("전체 AI 일일 접수 한도에 도달했습니다. 다음 한국 시간 자정 이후 다시 요청해 주세요.");
            }

            if (activeSubjects.size() >= properties.globalActiveLimit()) {
                throw AiUsageException.capacity("전체 AI 대기·실행 작업이 가득 찼습니다. 진행 중 작업 완료 후 다시 요청해 주세요.");
            }

            UUID reservationId = UUID.randomUUID();

            LocalDateTime leaseExpiresAt = feature == AiUsageFeature.FOLLOW_UP
                    ? now.plusSeconds(properties.followUpLeaseSeconds())
                    : null;

            repository.incrementGlobalDaily(usageDate);
            repository.incrementSubjectDaily(scope.subjectKey(), usageDate, feature);
            repository.insertReservation(reservationId, scope.subjectKey(), userId, feature, resourceId, now, leaseExpiresAt);

            return reservationId;

        } catch (DataAccessException exception) {
            throw AiUsageException.unavailable(exception);
        }
    }


    private void requireWritableTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("AI 접소는 기존 쓰기 트랜잭션 안에서 수행해야 합니다.");
        }
    }


    private record LockedScope(String subjectKey) {

    }


    public record Registration<T>(T value, Long resourceId) {

        public Registration {
            if (resourceId != null && resourceId <= 0) {
                throw new IllegalArgumentException("대상 ID는 양수여야 합니다.");
            }
        }

        public static <T> Registration<T> created(long resourceId, T value) {
            return new Registration<>(value, resourceId);
        }

        public static <T> Registration<T> existing(T value) {
            return new Registration<>(value, null);
        }
    }


    public record Admission<T>(T value, UUID reservationId) {

    }
}
