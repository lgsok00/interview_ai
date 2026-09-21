package com.interviewai.coverletter.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "cover_letter_versions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_cover_letter_versions_letter_version",
                        columnNames = {"cover_letter_id", "version_number"}
                )
        }
)
public class CoverLetterVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cover_letter_id", nullable = false)
    private CoverLetter coverLetter;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;


    protected CoverLetterVersion() {

    }


    private CoverLetterVersion(CoverLetter coverLetter, Integer versionNumber, String title, String content) {
        this.coverLetter = coverLetter;
        this.versionNumber = versionNumber;
        this.title = title;
        this.content = content;
    }


    public static CoverLetterVersion createInitial(CoverLetter coverLetter, String title, String content) {
        return new CoverLetterVersion(coverLetter, 1, title, content);
    }


    public static CoverLetterVersion create(CoverLetter coverLetter, Integer versionNumber, String title, String content) {
        return new CoverLetterVersion(coverLetter, versionNumber, title, content);
    }


    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
