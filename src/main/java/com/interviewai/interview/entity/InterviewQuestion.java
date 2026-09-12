package com.interviewai.interview.entity;

import com.interviewai.interview.enums.InterviewQuestionType;
import com.interviewai.interview.enums.QuestionGenerationSource;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Getter
@Table(
        name = "interview_questions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_interview_questions_session_sequence",
                        columnNames = {"session_id", "sequence_number"}
                )
        }
)
public class InterviewQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private InterviewSession session;

    @Column(name = "sequence_number", nullable = false, updatable = false)
    private Integer sequenceNumber;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "question_type", nullable = false, length = 20, updatable = false)
    private InterviewQuestionType questionType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "generation_source", nullable = false, length = 20, updatable = false)
    private QuestionGenerationSource generationSource;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT", updatable = false)
    private String content;

    @Column(name = "context_snapshot", columnDefinition = "MEDIUMTEXT", updatable = false)
    private String contextSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;


    protected InterviewQuestion() {

    }


    private InterviewQuestion(
            InterviewSession session,
            int sequenceNumber,
            InterviewQuestionType questionType,
            QuestionGenerationSource generationSource,
            String content,
            String contextSnapshot,
            LocalDateTime now
    ) {
        this.session = Objects.requireNonNull(session, "session은 필수입니다.");

        if (sequenceNumber < 1) {
            throw new IllegalArgumentException("sequenceNumber는 1 이상이어야 합니다.");
        }

        this.sequenceNumber = sequenceNumber;
        this.questionType = Objects.requireNonNull(questionType, "questionType은 필수입니다.");
        this.generationSource = Objects.requireNonNull(generationSource, "generationSource는 필수입니다.");
        this.content = requireText(content);
        this.contextSnapshot = normalizeNullableText(contextSnapshot);
        this.createdAt = Objects.requireNonNull(now, "now는 필수입니다.");
    }


    public static InterviewQuestion create(
            InterviewSession session,
            int sequenceNumber,
            InterviewQuestionType questionType,
            QuestionGenerationSource generationSource,
            String content,
            String contextSnapshot,
            LocalDateTime now
    ) {
        return new InterviewQuestion(
                session,
                sequenceNumber,
                questionType,
                generationSource,
                content,
                contextSnapshot,
                now
        );
    }


    private static String requireText(String value) {
        Objects.requireNonNull(value, "content는 필수입니다.");

        if (value.isBlank()) {
            throw new IllegalArgumentException("content는 비어 있을 수 없습니다.");
        }

        return value;
    }


    private static String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isEmpty() ? null : normalized;
    }
}
