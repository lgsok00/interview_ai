package com.interviewai.interview.repository;

import com.interviewai.interview.entity.InterviewQuestion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, Long> {

    List<InterviewQuestion> findAllBySession_IdOrderBySequenceNumberAsc(Long sessionId);

    Optional<InterviewQuestion> findByIdAndSession_Id(Long id, Long sessionId);

    @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InterviewQuestion> findByParentQuestionId(Long parentQuestionId);

    @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InterviewQuestion> findFirstBySession_IdOrderBySequenceNumberDesc(Long sessionId);
}
