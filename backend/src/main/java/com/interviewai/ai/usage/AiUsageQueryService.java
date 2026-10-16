package com.interviewai.ai.usage;

import com.interviewai.auth.exception.InvalidAccessTokenException;
import com.interviewai.global.error.CatalogException;
import com.interviewai.user.entity.User;
import com.interviewai.user.exception.UserNotFoundException;
import com.interviewai.user.repository.UserRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class AiUsageQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final AiUsageQueryRepository repository;
    private final AiUsageProperties properties;
    private final AiUsageIdentity identity;


    public AiUsageQueryService(
            UserRepository userRepository,
            AiUsageQueryRepository repository,
            AiUsageProperties properties,
            AiUsageIdentity identity
    ) {
        this.userRepository = userRepository;
        this.repository = repository;
        this.properties = properties;
        this.identity = identity;
    }


    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AiUsageResponse getUsage(String subject) {
        long userId = parseUserId(subject);

        try {
            User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);

            if (!user.isActive()) {
                throw new CatalogException(
                        HttpStatus.FORBIDDEN,
                        "USER_SUSPENDED",
                        "정지된 사용자 계정입니다."
                );
            }

            ZonedDateTime now = repository.currentUtcTime()
                    .atOffset(ZoneOffset.UTC)
                    .atZoneSameInstant(KST);

            LocalDate usageDate = now.toLocalDate();
            var resetAt = usageDate.plusDays(1)
                    .atStartOfDay(KST)
                    .toOffsetDateTime();

            if (!properties.enabled()) {
                return new AiUsageResponse(
                        false,
                        true,
                        KST.getId(),
                        usageDate,
                        resetAt,
                        null,
                        null,
                        List.of()
                );
            }

            String subjectKey = identity.subjectKey(user.getEmail());
            var snapshot = repository.findSnapshot(subjectKey, usageDate);

            List<AiUsageResponse.FeatureUsage> features =
                    Arrays.stream(AiUsageFeature.values())
                            .filter(AiUsageFeature::isChat)
                            .map(feature -> {
                                int limit = properties.dailyLimit(feature);
                                int used = snapshot.used().get(feature);

                                return new AiUsageResponse.FeatureUsage(
                                        feature,
                                        limit,
                                        used,
                                        Math.max(0, limit - used)
                                );
                            })
                            .toList();

            return new AiUsageResponse(
                    true,
                    properties.chatAdmissionEnabled(),
                    KST.getId(),
                    usageDate,
                    resetAt,
                    snapshot.active(),
                    properties.userActiveLimit(),
                    features
            );

        } catch (DataAccessException exception) {
            throw AiUsageException.unavailable(exception);
        }
    }


    private long parseUserId(String subject) {
        try {
            long userId = Long.parseLong(subject);

            if (userId <= 0) {
                throw new InvalidAccessTokenException();
            }

            return userId;

        } catch (NumberFormatException exception) {
            throw new InvalidAccessTokenException();
        }
    }
}
