package com.interviewai.resume.entity;

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
@Table(name = "resume_representatives")
public class ResumeRepresentative {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_id", nullable = false, unique = true)
    private Resume resume;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;


    protected ResumeRepresentative() {

    }


    private ResumeRepresentative(Long userId, Resume resume) {
        this.userId = userId;
        this.resume = resume;
    }


    public static ResumeRepresentative create(Long userId, Resume resume) {
        return new ResumeRepresentative(userId, resume);
    }


    public void changeResume(Resume resume) {
        this.resume = resume;
    }


    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
