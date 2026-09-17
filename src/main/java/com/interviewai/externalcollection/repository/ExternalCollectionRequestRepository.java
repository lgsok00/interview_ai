package com.interviewai.externalcollection.repository;

import com.interviewai.externalcollection.entity.ExternalCollectionRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExternalCollectionRequestRepository extends JpaRepository<ExternalCollectionRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT request
            FROM ExternalCollectionRequest request
            LEFT JOIN FETCH request.requestedBy
            LEFT JOIN FETCH request.approvedBy
            WHERE request.id = :requestId
            """)
    Optional<ExternalCollectionRequest> findLockedDetailById(@Param("requestId") Long requestId);
}
