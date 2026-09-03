package com.interviewai.resume.repository;

import com.interviewai.resume.entity.ResumeRepresentative;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ResumeRepresentativeRepository extends JpaRepository<ResumeRepresentative, Long> {

    boolean existsByUserIdAndResume_Id(Long userId, Long resumeId);
}
