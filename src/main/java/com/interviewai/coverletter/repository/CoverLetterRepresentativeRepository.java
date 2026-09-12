package com.interviewai.coverletter.repository;

import com.interviewai.coverletter.entity.CoverLetterRepresentative;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CoverLetterRepresentativeRepository extends JpaRepository<CoverLetterRepresentative, Long> {

    boolean existsByUserIdAndCoverLetter_Id(Long userId, Long coverLetterId);

    @Query("""
            SELECT representative
            FROM CoverLetterRepresentative representative
            JOIN FETCH representative.coverLetter coverLetter
            WHERE representative.userId = :userId
            """)
    Optional<CoverLetterRepresentative> findDetailByUserId(@Param("userId") Long userId);
}
