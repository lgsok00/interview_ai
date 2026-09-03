package com.interviewai.coverletter.repository;

import com.interviewai.coverletter.entity.CoverLetter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CoverLetterRepository extends JpaRepository<CoverLetter, Long> {

    List<CoverLetter> findAllByUser_IdOrderByUpdatedAtDesc(Long userId);

    Optional<CoverLetter> findByIdAndUser_Id(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT coverLetter
            FROM CoverLetter coverLetter
            WHERE coverLetter.id = :coverLetterId
              AND coverLetter.user.id = :userId
            """)
    Optional<CoverLetter> findOwnedForUpdate(@Param("coverLetterId") Long coverLetterId, @Param("userId") Long userId);
}
