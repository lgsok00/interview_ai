package com.interviewai.user.repository;

import com.interviewai.user.entity.User;
import com.interviewai.user.enums.AuthProvider;
import com.interviewai.user.enums.UserRole;
import com.interviewai.user.enums.UserStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT user
            FROM User user
            WHERE user.id = :userId
            """)
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

    @Query("""
            SELECT user
            FROM User user
            WHERE (
                LOWER(user.email) LIKE LOWER(:pattern) ESCAPE '!'
                OR LOWER(user.nickname) LIKE LOWER(:pattern) ESCAPE '!'
            )
            AND (:role IS NULL OR user.role = :role)
            AND (:provider IS NULL OR user.provider = :provider)
            AND (:status IS NULL OR user.status = :status)
            """)
    Page<User> searchForAdmin(
            @Param("pattern") String pattern,
            @Param("role") UserRole role,
            @Param("provider") AuthProvider provider,
            @Param("status") UserStatus status,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT user
            FROM User user
            WHERE user.role = com.interviewai.user.enums.UserRole.ADMIN
            ORDER BY user.id
            """)
    List<User> findAllAdminsForUpdate();
}
