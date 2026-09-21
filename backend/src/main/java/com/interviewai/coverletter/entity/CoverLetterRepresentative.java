package com.interviewai.coverletter.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "cover_letter_representatives")
public class CoverLetterRepresentative {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cover_letter_id", nullable = false, unique = true)
    private CoverLetter coverLetter;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;


    protected CoverLetterRepresentative() {

    }


    private CoverLetterRepresentative(Long userId, CoverLetter coverLetter) {
        this.userId = userId;
        this.coverLetter = coverLetter;
    }


    public static CoverLetterRepresentative create(Long userId, CoverLetter coverLetter) {
        return new CoverLetterRepresentative(userId, coverLetter);
    }


    public void changeCoverLetter(CoverLetter coverLetter) {
        this.coverLetter = coverLetter;
    }


    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
