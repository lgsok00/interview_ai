package com.interviewai.coverletter.repository;

import com.interviewai.coverletter.entity.CoverLetterRepresentative;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CoverLetterRepresentativeRepository extends JpaRepository<CoverLetterRepresentative, Long> {

    boolean existsByUserIdAndCoverLetter_Id(Long userId, Long coverLetterId);
}
