package com.interviewai.coverletter.entity;

import com.interviewai.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "cover_letters",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_cover_letters_user_id_id",
                        columnNames = {"user_id", "id"}
                )
        }
)
public class CoverLetter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "current_version_number", nullable = false)
    private Integer currentVersionNumber;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;


    protected CoverLetter() {

    }


    private CoverLetter(User user, String title) {
        this.user = user;
        this.title = title;
        this.currentVersionNumber = 1;
    }


    public static CoverLetter create(User user, String title) {
        return new CoverLetter(user, title);
    }


    public int addVersion(String title) {
        this.title = title;
        this.currentVersionNumber += 1;
        this.updatedAt = LocalDateTime.now();

        return this.currentVersionNumber;
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
