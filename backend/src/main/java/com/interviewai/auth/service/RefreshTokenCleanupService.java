package com.interviewai.auth.service;

import com.interviewai.auth.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class RefreshTokenCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;


    public RefreshTokenCleanupService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }


    @Transactional
    public int deleteExpiredBatch(Instant expiredAt, int batchSize) {
        return refreshTokenRepository.deleteExpiredBatch(expiredAt, batchSize);
    }
}
