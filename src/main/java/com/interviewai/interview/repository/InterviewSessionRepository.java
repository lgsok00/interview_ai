package com.interviewai.interview.repository;

import com.interviewai.interview.entity.InterviewSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {

    Page<InterviewSession> findAllByUser_IdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    Optional<InterviewSession> findByIdAndUser_Id(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT session
            FROM InterviewSession session
            WHERE session.id = :sessionId
              AND session.user.id = :userId
            """)
    Optional<InterviewSession> findOwnedByIdForUpdate(@Param("sessionId") Long sessionId, @Param("userId") Long userId);
}
