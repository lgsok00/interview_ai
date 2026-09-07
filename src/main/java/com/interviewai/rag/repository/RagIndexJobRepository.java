package com.interviewai.rag.repository;

import com.interviewai.rag.entity.RagIndexJobEntity;
import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface RagIndexJobRepository extends Repository<RagIndexJobEntity, Long> {

    RagIndexJobEntity save(RagIndexJobEntity job);

    Optional<RagIndexJobEntity> findById(Long id);
}
