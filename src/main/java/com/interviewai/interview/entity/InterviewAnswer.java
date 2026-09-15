package com.interviewai.interview.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Getter
@Table(
        name = "interview_answers",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_interview_answers_question",
                columnNames = "question_id"
        )
)
public class InterviewAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false, updatable = false)
    private InterviewQuestion question;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT", updatable = false)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;


    protected InterviewAnswer() {

    }


    private InterviewAnswer(InterviewQuestion question, String content, LocalDateTime now) {
        this.question = Objects.requireNonNull(question, "question은 필수입니다.");

        String normalized = Objects.requireNonNull(content, "content는 필수입니다.").strip();

        if (normalized.isBlank() || normalized.length() > 10000) {
            throw new IllegalArgumentException("답변은 1자 이상 10000자 이하여야 합니다.");
        }

        this.content = normalized;
        this.createdAt = Objects.requireNonNull(now, "now는 필수입니다.");
    }


    public static InterviewAnswer create(InterviewQuestion question, String content, LocalDateTime now) {
        return new InterviewAnswer(question, content, now);
    }
}
