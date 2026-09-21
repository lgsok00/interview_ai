package com.interviewai.interview.repository;

import com.interviewai.interview.entity.InterviewAnswer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewAnswerRepository extends JpaRepository<InterviewAnswer, Long> {

    @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InterviewAnswer> findByQuestion_Id(Long questionId);

    @Query("""
            SELECT answer
            FROM InterviewAnswer answer
            JOIN FETCH answer.question question
            WHERE question.session.id = :sessionId
            ORDER BY question.sequenceNumber ASC
            """)
    List<InterviewAnswer> findAllBySessionId(@Param("sessionId") Long sessionId);
}
