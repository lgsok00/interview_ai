package com.interviewai.jobposting.repository;

import com.interviewai.jobposting.entity.JobPosting;
import com.interviewai.jobposting.enums.EmploymentType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    String STATUS = """
            CASE
              WHEN j.manuallyClosed = true
                or (j.closesAt IS NOT NULL AND j.closesAt <= :now)
                THEN 'CLOSED'
              WHEN j.opensAt IS NOT NULL AND j.opensAt > :now
                THEN 'SCHEDULED'
              ELSE 'OPEN'
            END
            """;

    String FILTER = """
            FROM JobPosting j
            JOIN j.company c
            WHERE (:companyId IS NULL OR c.id = :companyId)
              AND j.title LIKE :pattern ESCAPE '!'
              AND (:status IS NULL OR
            """ + STATUS + " = :status)";

    @Query(
            value = """
                    SELECT j.id AS id,
                           c.id AS companyId,
                           c.name AS companyName,
                           j.title AS title,
                           j.jobRole AS jobRole,
                           j.employmentType AS employmentType,
                           j.location AS location,
                           j.opensAt AS opensAt,
                           j.closesAt AS closesAt,
                    """ + STATUS + " AS status " + FILTER,
            countQuery = "SELECT COUNT(j) " + FILTER
    )
    Page<SummaryRow> search(
            @Param("companyId") Long companyId,
            @Param("pattern") String pattern,
            @Param("status") String status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Query("""
            SELECT j
            FROM JobPosting j
            JOIN FETCH j.company
            WHERE j.id = :id
            """)
    Optional<JobPosting> findDetail(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT j
            FROM JobPosting j
            JOIN FETCH j.company
            WHERE j.id = :id
            """)
    Optional<JobPosting> findDetailForUpdate(@Param("id") Long id);

    @Query("SELECT j.company.id FROM JobPosting j WHERE j.id = :id")
    Optional<Long> findCompanyId(@Param("id") Long id);

    boolean existsByCompanyId(Long companyId);


    interface SummaryRow {
        Long getId();

        Long getCompanyId();

        String getCompanyName();

        String getTitle();

        String getJobRole();

        EmploymentType getEmploymentType();

        String getLocation();

        String getStatus();

        LocalDateTime getOpensAt();

        LocalDateTime getClosesAt();
    }
}
