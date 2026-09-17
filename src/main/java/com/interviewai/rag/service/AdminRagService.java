package com.interviewai.rag.service;

import com.interviewai.global.error.CatalogException;
import com.interviewai.global.security.AdminAuthorizationService;
import com.interviewai.global.validation.CatalogInput;
import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.dto.AdminRagResponse;
import com.interviewai.rag.entity.RagIndexSource;
import com.interviewai.rag.index.RagIndexJobStatus;
import com.interviewai.rag.index.RagIndexOperation;
import com.interviewai.rag.repository.AdminRagRepository;
import com.interviewai.rag.repository.RagIndexSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminRagService {

    private final AdminAuthorizationService authorizationService;
    private final AdminRagRepository adminRepository;
    private final RagIndexSourceRepository sourceRepository;


    private static void validateFilter(RagSourceType sourceType, Long sourceId) {
        if (sourceId != null) {
            CatalogInput.id(sourceId, "sourceId");
            requireSourceType(sourceType);
        }
    }


    private static void requireSourceType(RagSourceType sourceType) {
        if (sourceType == null) {
            throw CatalogException.invalid("sourceType", "필수 값입니다.");
        }
    }


    private static CatalogException conflict(String code, String message) {
        return new CatalogException(HttpStatus.CONFLICT, code, message);
    }


    public AdminRagResponse.Page<AdminRagResponse.Source> sources(
            String subject,
            RagSourceType sourceType,
            Long sourceId,
            int page,
            int size
    ) {
        authorizationService.requireAdmin(subject);
        validateFilter(sourceType, sourceId);
        CatalogInput.page(page, size);

        return adminRepository.findSources(sourceType, sourceId, page, size);
    }


    public AdminRagResponse.Source source(String subject, RagSourceType sourceType, long sourceId) {
        authorizationService.requireAdmin(subject);
        CatalogInput.id(sourceId, "sourceId");
        requireSourceType(sourceType);

        return adminRepository.findSource(sourceType, sourceId)
                .orElseThrow(() -> new CatalogException(
                        HttpStatus.NOT_FOUND,
                        "RAG_INDEX_SOURCE_NOT_FOUND",
                        "RAG 원본 관리 정보를 찾을 수 없습니다."
                ));
    }


    public AdminRagResponse.Page<AdminRagResponse.Job> jobs(
            String subject,
            RagSourceType sourceType,
            Long sourceId,
            RagIndexJobStatus status,
            RagIndexOperation operation,
            int page,
            int size
    ) {
        authorizationService.requireAdmin(subject);
        validateFilter(sourceType, sourceId);
        CatalogInput.page(page, size);

        return adminRepository.findJobs(sourceType, sourceId, status, operation, page, size);
    }


    public AdminRagResponse.Job job(String subject, Long jobId) {
        authorizationService.requireAdmin(subject);
        CatalogInput.id(jobId, "jobId");

        return findJob(jobId);
    }


    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminRagResponse.Job retry(String subject, Long jobId) {
        authorizationService.requireAdmin(subject);
        CatalogInput.id(jobId, "jobId");

        AdminRagResponse.Job initial = findJob(jobId);

        RagIndexSource source = sourceRepository
                .findLocked(initial.sourceType(), initial.sourceId())
                .orElseThrow(() -> new IllegalStateException("RAG 원본 관리 행을 찾을 수 없습니다."));

        AdminRagResponse.Job current = findJob(jobId);

        if (current.status() != RagIndexJobStatus.FAILED) {
            throw conflict("RAG_JOB_NOT_FAILED", "실패한 RAG 작업만 재시도할 수 있습니다.");
        }

        if (current.manualRetryCount() >= 2) {
            throw conflict("RAG_JOB_RETRY_LIMIT_EXCEEDED", "수동 재시도는 최대 2회까지 가능합니다.");
        }

        if (current.operation() == RagIndexOperation.UPSERT
                && (current.sourceSequence() != source.getLastSequence()
                || current.sourceSequence() <= source.getTombstoneSequence())) {
            throw conflict("RAG_JOB_SUPERSEDED", "이후 변경이 등록된 작업입니다. 현재 원본 재색인을 사용하세요.");

        }

        int updated = adminRepository.retryFailed(jobId, RagSourceChangeRegistrationService.MAX_ATTEMPTS);

        if (updated != 1) {
            throw conflict("RAG_JOB_RETRY_CONFLICT", "현재 작업 상태에서는 재시도를 접수할 수 없습니다.");
        }

        return findJob(jobId);
    }


    private AdminRagResponse.Job findJob(Long jobId) {
        return adminRepository.findJob(jobId)
                .orElseThrow(() -> new CatalogException(
                        HttpStatus.NOT_FOUND,
                        "RAG_JOB_NOT_FOUND",
                        "RAG 작업을 찾을 수 없습니다."
                ));
    }
}
