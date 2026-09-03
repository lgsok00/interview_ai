package com.interviewai.resume.storage;

import com.interviewai.resume.exception.ResumeStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class ResumeFileTransactionCleanup {

    private static final Logger log = LoggerFactory.getLogger(ResumeFileTransactionCleanup.class);

    private final ResumeFileStorage fileStorage;


    public ResumeFileTransactionCleanup(ResumeFileStorage fileStorage) {
        this.fileStorage = fileStorage;
    }


    public void deleteAfterCommit(String storageKey) {
        requireActiveTransaction();

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        safelyDelete(storageKey);
                    }
                }
        );
    }


    public void deleteAfterRollback(String storageKey) {
        requireActiveTransaction();

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            safelyDelete(storageKey);
                        }
                    }
                }
        );
    }


    private void requireActiveTransaction() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("활성 트랜잭션에서만 파일 정리를 예약할 수 있습니다.");
        }
    }


    private void safelyDelete(String storageKey) {
        try {
            fileStorage.delete(storageKey);

        } catch (ResumeStorageException exception) {
            log.error("이력서 파일 정리에 실패했습니다. storageKey={}", storageKey, exception);
        }
    }
}
