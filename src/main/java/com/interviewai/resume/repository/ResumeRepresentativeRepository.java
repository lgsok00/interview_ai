package com.interviewai.resume.repository;

import com.interviewai.resume.entity.ResumeRepresentative;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ResumeRepresentativeRepository extends JpaRepository<ResumeRepresentative, Long> {

    boolean existsByUserIdAndResume_Id(Long userId, Long resumeId);

    @Query("""
            SELECT representative
            FROM ResumeRepresentative representative
            JOIN FETCH representative.resume resume
            WHERE representative.userId = :userId
            """)
    Optional<ResumeRepresentative> findDetailByUserId(@Param("userId") Long userId);
}
