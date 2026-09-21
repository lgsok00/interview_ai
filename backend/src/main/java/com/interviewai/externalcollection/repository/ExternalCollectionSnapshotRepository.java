package com.interviewai.externalcollection.repository;

import com.interviewai.externalcollection.entity.ExternalCollectionSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExternalCollectionSnapshotRepository extends JpaRepository<ExternalCollectionSnapshot, Long> {

    List<ExternalCollectionSnapshot> findAllByRequest_IdOrderByCollectedAtDescIdDesc(Long requestId);

    Optional<ExternalCollectionSnapshot> findByIdAndRequest_Id(Long snapshotId, Long requestId);
}
