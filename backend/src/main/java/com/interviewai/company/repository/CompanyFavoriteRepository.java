package com.interviewai.company.repository;

import com.interviewai.company.entity.CompanyFavorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface CompanyFavoriteRepository extends JpaRepository<CompanyFavorite, Long> {

    boolean existsByUserIdAndCompanyId(Long userId, Long companyId);

    @Query(
            value = """
                    SELECT c.id AS id,
                           c.name AS name,
                           c.industry AS industry,
                           c.location AS location,
                           TRUE AS favorite
                    FROM CompanyFavorite f
                    JOIN f.company c
                    WHERE f.user.id = :userId
                    ORDER BY f.createdAt DESC, f.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(f)
                    FROM CompanyFavorite f
                    WHERE f.user.id = :userId
                    """
    )
    Page<CompanyRepository.SummaryRow> findSummaries(@Param("userId") Long userId, Pageable pageable);

    @Modifying
    @Query(
            value = """
                    INSERT INTO company_favorites (user_id, company_id, created_at)
                    VALUES (:userId, :companyId, :now)
                    ON DUPLICATE KEY UPDATE id = id
                    """,
            nativeQuery = true
    )
    void add(@Param("userId") Long userId, @Param("companyId") Long companyId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("""
            DELETE FROM CompanyFavorite f
            WHERE f.user.id = :userId AND f.company.id = :companyId
            """)
    void remove(@Param("userId") Long userId, @Param("companyId") Long companyId);
}
