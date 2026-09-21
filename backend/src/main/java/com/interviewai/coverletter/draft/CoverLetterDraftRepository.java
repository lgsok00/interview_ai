package com.interviewai.coverletter.draft;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CoverLetterDraftRepository extends JpaRepository<CoverLetterDraft, Long> {

    @Query("""
            SELECT draft
            FROM CoverLetterDraft draft
            WHERE draft.user.id = :userId
              AND draft.coverLetter.id = :coverLetterId
            ORDER BY draft.createdAt DESC, draft.id DESC
            """)
    List<CoverLetterDraft> findOwnedAll(@Param("userId") Long userId, @Param("coverLetterId") Long coverLetterId);

    @Query("""
            SELECT draft
            FROM CoverLetterDraft draft
            WHERE draft.id = :draftId
              AND draft.user.id = :userId
              AND draft.coverLetter.id = :coverLetterId
            """)
    Optional<CoverLetterDraft> findOwned(
            @Param("draftId") Long draftId,
            @Param("userId") Long userId,
            @Param("coverLetterId") Long coverLetterId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT draft
            FROM CoverLetterDraft draft
            WHERE draft.id = :draftId
              AND draft.user.id = :userId
              AND draft.coverLetter.id = :coverLetterId
            """)
    Optional<CoverLetterDraft> findOwnedForUpdate(
            @Param("draftId") Long draftId,
            @Param("userId") Long userId,
            @Param("coverLetterId") Long coverLetterId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT draft
            FROM CoverLetterDraft draft
            WHERE draft.id = :draftId
            """)
    Optional<CoverLetterDraft> findByIdForUpdate(@Param("draftId") Long draftId);

    @Query(
            value = """
                    SELECT *
                    FROM cover_letter_drafts
                    WHERE (
                        status = 'PENDING'
                            AND available_at <= UTC_TIMESTAMP(6)
                            AND attempt_count < 3
                    )
                    OR (
                        status = 'RUNNING'
                            AND lease_expires_at <= UTC_TIMESTAMP(6)
                            AND attempt_count < 3
                    )
                    ORDER BY
                        IF(status = 'RUNNING', 0, 1),
                        COALESCE(lease_expires_at, available_at),
                        id
                    LIMIT 1
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true
    )
    Optional<CoverLetterDraft> findNextClaimableForUpdate();


    @Query(
            value = """
                    SELECT *
                    FROM cover_letter_drafts
                    WHERE status = 'RUNNING'
                      AND lease_expires_at <= UTC_TIMESTAMP(6)
                      AND attempt_count >= 3
                    ORDER BY lease_expires_at, id
                    LIMIT 1
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true
    )
    Optional<CoverLetterDraft> findNextExhaustedExpiredForUpdate();
}
