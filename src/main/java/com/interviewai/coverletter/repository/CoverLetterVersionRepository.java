package com.interviewai.coverletter.repository;

import com.interviewai.coverletter.entity.CoverLetterVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CoverLetterVersionRepository extends JpaRepository<CoverLetterVersion, Long> {

    Optional<CoverLetterVersion> findByCoverLetter_IdAndVersionNumber(Long coverLetterId, Integer versionNumber);

    List<CoverLetterVersion> findAllByCoverLetter_IdOrderByVersionNumberDesc(Long coverLetterId);
}
