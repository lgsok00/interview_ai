package com.interviewai.interview.repository;

import com.interviewai.interview.entity.InterviewAnswerEvaluation;
import com.interviewai.interview.enums.AnswerEvaluationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewAnswerEvaluationRepository extends JpaRepository<InterviewAnswerEvaluation, Long> {

    Optional<InterviewAnswerEvaluation> findByAnswer_Id(Long answerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT evaluation
            FROM InterviewAnswerEvaluation evaluation
            WHERE evaluation.id = :evaluationId
            """)
    Optional<InterviewAnswerEvaluation> findByIdForUpdate(@Param("evaluationId") Long evaluationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT evaluation
            FROM InterviewAnswerEvaluation evaluation
            WHERE evaluation.answer.id = :answerId
            """)
    Optional<InterviewAnswerEvaluation> findByAnswerIdForUpdate(@Param("answerId") Long answerId);

    @Query("""
            SELECT evaluation.id
            FROM InterviewAnswerEvaluation evaluation
            WHERE evaluation.status = :status
              AND evaluation.availableAt <= :now
            ORDER BY evaluation.availableAt ASC, evaluation.id ASC
            """)
    List<Long> findPendingIds(
            @Param("status") AnswerEvaluationStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Query("""
            SELECT evaluation.id
            FROM InterviewAnswerEvaluation evaluation
            WHERE evaluation.status = :status
              AND evaluation.leaseExpiresAt <= :now
            ORDER BY evaluation.leaseExpiresAt ASC, evaluation.id ASC
            """)
    List<Long> findExpiredLeaseIds(
            @Param("status") AnswerEvaluationStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Query(value = """
            SELECT *
            FROM interview_answer_evaluations
            WHERE status = 'PENDING'
              AND available_at <= UTC_TIMESTAMP(6)
            ORDER BY available_at, id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<InterviewAnswerEvaluation> findNextPendingForUpdate();

    @Query(value = """
            SELECT *
            FROM interview_answer_evaluations
            WHERE status = 'PROCESSING'
              AND lease_expires_at <= UTC_TIMESTAMP(6)
            ORDER BY lease_expires_at, id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<InterviewAnswerEvaluation> findNextExpiredForUpdate();
}
