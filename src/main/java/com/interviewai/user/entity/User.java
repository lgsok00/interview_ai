package com.interviewai.user.entity;

import com.interviewai.user.enums.AuthProvider;
import com.interviewai.user.enums.UserRole;
import com.interviewai.user.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Getter
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_users_email",
                        columnNames = "email"
                ),
                @UniqueConstraint(
                        name = "uk_users_provider_account",
                        columnNames = {"provider", "provider_id"}
                )
        }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_id")
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;


    protected User() {
    }


    private User(String email, String passwordHash, String nickname, AuthProvider provider, String providerId, UserRole role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.provider = provider;
        this.providerId = providerId;
        this.role = role;
        this.status = UserStatus.ACTIVE;
    }


    public static User createLocalUser(String email, String encodedPassword, String nickname) {
        return new User(email, encodedPassword, nickname, AuthProvider.LOCAL, null, UserRole.USER);
    }


    public static User createGoogleUser(String email, String nickname, String providerId) {
        return new User(email, null, nickname, AuthProvider.GOOGLE, providerId, UserRole.USER);
    }


    public static User createGithubUser(String email, String nickname, String providerId) {
        return new User(email, null, nickname, AuthProvider.GITHUB, providerId, UserRole.USER);
    }


    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }


    public void changePassword(String encodedPassword) {
        this.passwordHash = encodedPassword;
    }


    public void changeRole(UserRole role) {
        this.role = Objects.requireNonNull(role, "role은 필수입니다.");
    }


    public void suspend(LocalDateTime suspendedAt) {
        this.status = UserStatus.SUSPENDED;
        this.suspendedAt = Objects.requireNonNull(suspendedAt, "정지 시각은 필수입니다.");
    }


    public void activate() {
        this.status = UserStatus.ACTIVE;
        this.suspendedAt = null;
    }


    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }


    @PrePersist
    private void prePersist() {
        LocalDateTime now = LocalDateTime.now();

        this.createdAt = now;
        this.updatedAt = now;
    }


    @PreUpdate
    private void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
