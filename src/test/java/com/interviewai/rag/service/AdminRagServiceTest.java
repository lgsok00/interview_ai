package com.interviewai.rag.service;

import com.interviewai.global.error.CatalogException;
import com.interviewai.global.security.AdminAuthorizationService;
import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.dto.AdminRagResponse;
import com.interviewai.rag.entity.RagIndexSource;
import com.interviewai.rag.index.RagIndexJobStatus;
import com.interviewai.rag.index.RagIndexOperation;
import com.interviewai.rag.repository.AdminRagRepository;
import com.interviewai.rag.repository.RagIndexSourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminRagServiceTest {

    @Mock
    AdminAuthorizationService authorization;
    @Mock
    AdminRagRepository repository;
    @Mock
    RagIndexSourceRepository sources;
    @Mock
    RagIndexSource source;
    AdminRagService service;

    @BeforeEach
    void setUp() {
        service = new AdminRagService(authorization, repository, sources);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void acceptsPageSizeBoundariesAndForwardsFilters(int size) {
        var sourcePage = AdminRagResponse.Page.<AdminRagResponse.Source>of(List.of(), 2, size, 0);
        var jobPage = AdminRagResponse.Page.<AdminRagResponse.Job>of(List.of(), 2, size, 0);
        when(repository.findSources(RagSourceType.RESUME, 9L, 2, size)).thenReturn(sourcePage);
        when(repository.findJobs(RagSourceType.RESUME, 9L, RagIndexJobStatus.FAILED,
                RagIndexOperation.DELETE, 2, size)).thenReturn(jobPage);

        assertThat(service.sources("1", RagSourceType.RESUME, 9L, 2, size)).isSameAs(sourcePage);
        assertThat(service.jobs("1", RagSourceType.RESUME, 9L, RagIndexJobStatus.FAILED,
                RagIndexOperation.DELETE, 2, size)).isSameAs(jobPage);
        verify(authorization, times(2)).requireAdmin("1");
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,101"})
    void rejectsInvalidPagesBeforeQuery(int page, int size) {
        assertError(() -> service.sources("1", null, null, page, size),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertError(() -> service.jobs("1", null, null, null, null, page, size),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        verifyNoInteractions(repository, sources);
    }

    @Test
    void rejectsSourceIdWithoutTypeAndNonpositiveIds() {
        assertError(() -> service.sources("1", null, 1L, 0, 20), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertError(() -> service.jobs("1", null, 1L, null, null, 0, 20), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertError(() -> service.sources("1", RagSourceType.COMPANY, 0L, 0, 20), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertError(() -> service.source("1", null, 1L), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertError(() -> service.source("1", RagSourceType.COMPANY, -1L), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertError(() -> service.job("1", null), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertError(() -> service.retry("1", 0L), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        verifyNoInteractions(repository, sources);
    }

    @Test
    void authorizesEveryOperationBeforeReadingOrChangingData() {
        when(authorization.requireAdmin("2")).thenThrow(
                new CatalogException(HttpStatus.FORBIDDEN, "FORBIDDEN", "관리자 권한 필요"));
        List<Runnable> operations = List.of(
                () -> service.sources("2", null, null, 0, 20),
                () -> service.source("2", RagSourceType.RESUME, 9L),
                () -> service.jobs("2", null, null, null, null, 0, 20),
                () -> service.job("2", 10L),
                () -> service.retry("2", 10L)
        );
        operations.forEach(operation -> assertError(operation, HttpStatus.FORBIDDEN, "FORBIDDEN"));
        verifyNoInteractions(repository, sources);
    }

    @Test
    void returnsSafeDetailsAndReportsMissingRows() {
        var info = new AdminRagResponse.Source(1, RagSourceType.RESUME, 9, 3, null, 0, 3);
        var job = job(RagIndexJobStatus.FAILED, RagIndexOperation.DELETE, 0);
        when(repository.findSource(RagSourceType.RESUME, 9L)).thenReturn(Optional.of(info));
        when(repository.findJob(10L)).thenReturn(Optional.of(job));
        assertThat(service.source("1", RagSourceType.RESUME, 9L)).isEqualTo(info);
        assertThat(service.job("1", 10L)).isEqualTo(job);
        assertError(() -> service.source("1", RagSourceType.COMPANY, 99L),
                HttpStatus.NOT_FOUND, "RAG_INDEX_SOURCE_NOT_FOUND");
        assertError(() -> service.job("1", 99L), HttpStatus.NOT_FOUND, "RAG_JOB_NOT_FOUND");
        assertError(() -> service.retry("1", 99L), HttpStatus.NOT_FOUND, "RAG_JOB_NOT_FOUND");
        verifyNoInteractions(sources);
    }

    @Test
    void retriesCurrentFailedUpsertAfterLockAndReturnsReloadedState() {
        var failed = job(RagIndexJobStatus.FAILED, RagIndexOperation.UPSERT, 0);
        var pending = job(RagIndexJobStatus.PENDING, RagIndexOperation.UPSERT, 1);
        when(repository.findJob(10L))
                .thenReturn(Optional.of(failed))
                .thenReturn(Optional.of(failed))
                .thenReturn(Optional.of(pending));
        when(sources.findLocked(RagSourceType.RESUME, 9L)).thenReturn(Optional.of(source));
        when(source.getLastSequence()).thenReturn(3L);
        when(repository.retryFailed(10L, 3)).thenReturn(1);

        assertThat(service.retry("1", 10L)).isSameAs(pending);
        var order = inOrder(authorization, repository, sources);
        order.verify(authorization).requireAdmin("1");
        order.verify(repository).findJob(10L);
        order.verify(sources).findLocked(RagSourceType.RESUME, 9L);
        order.verify(repository).findJob(10L);
        order.verify(repository).retryFailed(10L, 3);
        order.verify(repository).findJob(10L);
    }

    @ParameterizedTest
    @EnumSource(value = RagIndexJobStatus.class, names = {"PENDING", "RUNNING", "SUCCEEDED", "CANCELLED"})
    void rejectsNonfailedStatusReadAfterLock(RagIndexJobStatus status) {
        when(repository.findJob(10L))
                .thenReturn(Optional.of(job(RagIndexJobStatus.FAILED, RagIndexOperation.UPSERT, 0)))
                .thenReturn(Optional.of(job(status, RagIndexOperation.UPSERT, 0)));
        when(sources.findLocked(RagSourceType.RESUME, 9L)).thenReturn(Optional.of(source));

        assertError(() -> service.retry("1", 10L), HttpStatus.CONFLICT, "RAG_JOB_NOT_FAILED");
        verify(repository, never()).retryFailed(anyLong(), anyInt());
    }

    @Test
    void rejectsThirdManualRetry() {
        prepareRetry(RagIndexOperation.UPSERT, 2);
        assertError(() -> service.retry("1", 10L), HttpStatus.CONFLICT, "RAG_JOB_RETRY_LIMIT_EXCEEDED");
        verify(repository, never()).retryFailed(anyLong(), anyInt());
    }

    @ParameterizedTest
    @CsvSource({"4,0", "3,3"})
    void rejectsSupersededOrTombstonedUpsert(long lastSequence, long tombstone) {
        prepareRetry(RagIndexOperation.UPSERT, 0);
        when(source.getLastSequence()).thenReturn(lastSequence);
        if (lastSequence == 3) {
            when(source.getTombstoneSequence()).thenReturn(tombstone);
        }
        assertError(() -> service.retry("1", 10L), HttpStatus.CONFLICT, "RAG_JOB_SUPERSEDED");
        verify(repository, never()).retryFailed(anyLong(), anyInt());
    }

    @Test
    void allowsOlderDeleteWithoutInspectingLatestSequence() {
        prepareRetry(RagIndexOperation.DELETE, 0);
        when(repository.retryFailed(10L, 3)).thenReturn(1);
        service.retry("1", 10L);
        verify(repository).retryFailed(10L, 3);
        verifyNoInteractions(source);
    }

    @Test
    void reportsConditionalUpdateConflict() {
        prepareRetry(RagIndexOperation.DELETE, 0);
        assertError(() -> service.retry("1", 10L), HttpStatus.CONFLICT, "RAG_JOB_RETRY_CONFLICT");
    }

    private void prepareRetry(RagIndexOperation operation, int retries) {
        when(repository.findJob(10L)).thenReturn(Optional.of(job(RagIndexJobStatus.FAILED, operation, retries)));
        when(sources.findLocked(RagSourceType.RESUME, 9L)).thenReturn(Optional.of(source));
    }

    private AdminRagResponse.Job job(RagIndexJobStatus status, RagIndexOperation operation, int retries) {
        Instant now = Instant.parse("2026-09-17T00:00:00Z");
        return new AdminRagResponse.Job(10, RagSourceType.RESUME, 9, 3, operation, status,
                3, 3 + retries * 3, retries, null, null, null, now, now);
    }

    private void assertError(Runnable action, HttpStatus status, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CatalogException.class, exception -> {
            assertThat(exception.getStatus()).isEqualTo(status);
            assertThat(exception.getCode()).isEqualTo(code);
        });
    }
}
