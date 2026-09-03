package com.interviewai.resume.repository;

import com.interviewai.resume.entity.Resume;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResumeRepository extends JpaRepository<Resume, Long> {

    List<Resume> findAllByUser_IdOrderByUpdatedAtDesc(Long userId);

    Optional<Resume> findByIdAndUser_Id(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT resume
            FROM Resume resume
            WHERE resume.id = :resumeId
              AND resume.user.id = :userId
            """)
    Optional<Resume> findOwnedForUpdate(@Param("resumeId") Long resumeId, @Param("userId") Long userId);

    @Query("""
            SELECT resume.storageKey
            FROM Resume resume
            WHERE resume.user.id = :userId
            """)
    List<String> findStorageKeysByUserId(@Param("userId") Long userId);
}
