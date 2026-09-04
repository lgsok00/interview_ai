package com.interviewai.company.repository;

import com.interviewai.company.entity.Company;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    @Query(
            value = """
                    SELECT c.id AS id,
                           c.name AS name,
                           c.industry AS industry,
                           c.location AS location,
                           CASE WHEN f.id IS NULL THEN FALSE ELSE TRUE END AS favorite
                    FROM Company c
                    LEFT JOIN CompanyFavorite f
                      ON f.company.id = c.id AND f.user.id = :userId
                    WHERE c.name LIKE :pattern ESCAPE '!'
                    """,
            countQuery = """
                    SELECT COUNT(c)
                    FROM Company c
                    WHERE c.name LIKE :pattern ESCAPE '!'
                    """
    )
    Page<SummaryRow> search(@Param("userId") Long userId, @Param("pattern") String pattern, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Company c WHERE c.id = :id")
    Optional<Company> findLockedById(@Param("id") Long id);


    interface SummaryRow {
        Long getId();

        String getName();

        String getIndustry();

        String getLocation();

        boolean getFavorite();
    }
}
