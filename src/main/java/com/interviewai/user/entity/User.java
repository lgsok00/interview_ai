package com.interviewai.user.entity;

import com.interviewai.user.enums.AuthProvider;
import com.interviewai.user.enums.UserRole;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

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
