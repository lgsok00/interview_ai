package com.interviewai.rag.service;

import com.interviewai.company.entity.Company;
import com.interviewai.company.repository.CompanyRepository;
import com.interviewai.coverletter.entity.CoverLetter;
import com.interviewai.coverletter.entity.CoverLetterVersion;
import com.interviewai.coverletter.repository.CoverLetterRepository;
import com.interviewai.coverletter.repository.CoverLetterVersionRepository;
import com.interviewai.global.error.CatalogException;
import com.interviewai.global.security.AdminAuthorizationService;
import com.interviewai.jobposting.entity.JobPosting;
import com.interviewai.jobposting.enums.EmploymentType;
import com.interviewai.jobposting.repository.JobPostingRepository;
import com.interviewai.rag.document.RagSourceSnapshotFactory;
import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.dto.AdminRagResponse;
import com.interviewai.rag.entity.RagIndexJobEntity;
import com.interviewai.rag.index.RagIndexJobStatus;
import com.interviewai.rag.index.RagIndexOperation;
import com.interviewai.rag.index.RagIndexTarget;
import com.interviewai.rag.repository.AdminRagRepository;
import com.interviewai.resume.entity.Resume;
import com.interviewai.resume.repository.ResumeRepository;
import com.interviewai.user.entity.User;
import com.interviewai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminRagReindexServiceTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 17, 0, 0);
    @Mock
    AdminAuthorizationService authorization;
    @Mock
    CompanyRepository companies;
    @Mock
    JobPostingRepository postings;
    @Mock
    CoverLetterRepository letters;
    @Mock
    CoverLetterVersionRepository versions;
    @Mock
    ResumeRepository resumes;
    @Mock
    UserRepository users;
    @Mock
    RagIndexJobRegistrationService registration;
    @Mock
    AdminRagRepository queries;
    AdminRagReindexService service;
    User owner;
    Company company;

    @BeforeEach
    void setUp() {
        service = new AdminRagReindexService(authorization, companies, postings, letters, versions,
                resumes, users, new RagSourceSnapshotFactory(), registration, queries);
        owner = identified(User.createLocalUser("owner@example.com", "encoded", "소유자"), 2L);
        company = identified(Company.create("현재 기업", "IT", "현재 기업 본문", null, "서울", NOW), 10L);
    }

    @Test
    void reindexesLockedCompanyWithCurrentContent() {
        when(companies.findLockedById(10L)).thenReturn(Optional.of(company));
        prepareRegistration(RagSourceType.COMPANY, 10L);
        service.reindex("1", RagSourceType.COMPANY, 10L);
        var target = registeredTarget();
        assertThat(target.snapshot().title()).isEqualTo("현재 기업");
        assertThat(target.snapshot().content()).contains("현재 기업 본문");
        assertThat(target.snapshot().companyId()).isEqualTo(10L);
        assertThat(target.snapshot().ownerUserId()).isNull();
        var order = inOrder(authorization, companies, registration);
        order.verify(authorization).requireAdmin("1");
        order.verify(companies).findLockedById(10L);
        order.verify(registration).registerUpsert(any(), eq(3));
    }

    @Test
    void locksCompanyBeforePostingAndUsesCurrentPosting() {
        JobPosting posting = identified(JobPosting.create(company, "현재 공고", "백엔드",
                EmploymentType.FULL_TIME, "서울", "공고 내용", null, null, null, false, NOW), 11L);
        when(postings.findCompanyId(11L)).thenReturn(Optional.of(10L));
        when(companies.findLockedById(10L)).thenReturn(Optional.of(company));
        when(postings.findDetailForUpdate(11L)).thenReturn(Optional.of(posting));
        prepareRegistration(RagSourceType.JOB_POSTING, 11L);

        service.reindex("1", RagSourceType.JOB_POSTING, 11L);
        assertThat(registeredTarget().snapshot().content()).contains("현재 기업", "공고 내용");
        var order = inOrder(companies, postings, registration);
        order.verify(postings).findCompanyId(11L);
        order.verify(companies).findLockedById(10L);
        order.verify(postings).findDetailForUpdate(11L);
        order.verify(registration).registerUpsert(any(), eq(3));
    }

    @Test
    void locksOwnerBeforeLetterAndUsesOnlyCurrentVersion() {
        CoverLetter letter = prepareLetter();
        letter.addVersion("최신 제목");
        when(versions.findByCoverLetter_IdAndVersionNumber(12L, 2))
                .thenReturn(Optional.of(CoverLetterVersion.create(letter, 2, "최신 제목", "최신 비공개 본문")));
        prepareRegistration(RagSourceType.COVER_LETTER, 12L);

        service.reindex("1", RagSourceType.COVER_LETTER, 12L);
        var snapshot = registeredTarget().snapshot();
        assertThat(snapshot.title()).isEqualTo("최신 제목");
        assertThat(snapshot.content()).contains("버전: 2", "최신 비공개 본문");
        assertThat(snapshot.ownerUserId()).isEqualTo(2L);
        var order = inOrder(letters, users, versions, registration);
        order.verify(letters).findOwnedId(12L);
        order.verify(users).findByIdForUpdate(2L);
        order.verify(letters).findOwnedForUpdate(12L, 2L);
        order.verify(versions).findByCoverLetter_IdAndVersionNumber(12L, 2);
        order.verify(registration).registerUpsert(any(), eq(3));
    }

    @Test
    void locksOwnerBeforeResumeAndUsesExtractedText() {
        Resume resume = prepareResume();
        resume.completeExtraction("현재 추출 본문");
        prepareRegistration(RagSourceType.RESUME, 13L);

        service.reindex("1", RagSourceType.RESUME, 13L);
        assertThat(registeredTarget().snapshot().content()).contains("현재 추출 본문");
        var order = inOrder(resumes, users, registration);
        order.verify(resumes).findOwnedId(13L);
        order.verify(users).findByIdForUpdate(2L);
        order.verify(resumes).findOwnedForUpdate(13L, 2L);
        order.verify(registration).registerUpsert(any(), eq(3));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "FAILED", "EMPTY", "BLANK"})
    void rejectsUnavailableResumeWithoutRegisteringEvenDelete(String state) {
        Resume resume = prepareResume();
        switch (state) {
            case "FAILED" -> resume.failExtraction("EXTRACTION_FAILED");
            case "EMPTY" -> resume.completeExtraction("");
            case "BLANK" -> resume.completeExtraction(" \n ");
            default -> {
            }
        }
        assertError(() -> service.reindex("1", RagSourceType.RESUME, 13L),
                HttpStatus.CONFLICT, "RAG_SOURCE_NOT_READY");
        verifyNoInteractions(registration, queries);
    }

    @Test
    void rejectsMissingCurrentLetterVersion() {
        prepareLetter();
        assertError(() -> service.reindex("1", RagSourceType.COVER_LETTER, 12L),
                HttpStatus.CONFLICT, "RAG_SOURCE_NOT_READY");
        verifyNoInteractions(registration, queries);
    }

    @ParameterizedTest
    @EnumSource(RagSourceType.class)
    void rejectsMissingCurrentSource(RagSourceType type) {
        assertError(() -> service.reindex("1", type, 99L), HttpStatus.NOT_FOUND, "RAG_SOURCE_NOT_FOUND");
        verifyNoInteractions(registration, queries);
    }

    @ParameterizedTest
    @EnumSource(value = RagSourceType.class, names = {"COVER_LETTER", "RESUME"})
    void rejectsOwnerDeletedBetweenLookupAndLock(RagSourceType type) {
        if (type == RagSourceType.COVER_LETTER) {
            when(letters.findOwnedId(99L)).thenReturn(Optional.of(2L));
        } else {
            when(resumes.findOwnedId(99L)).thenReturn(Optional.of(2L));
        }
        assertError(() -> service.reindex("1", type, 99L), HttpStatus.NOT_FOUND, "RAG_SOURCE_NOT_FOUND");
        verifyNoInteractions(registration, queries);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsInvalidId(Long id) {
        assertError(() -> service.reindex("1", RagSourceType.COMPANY, id), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        verifyNoInteractions(companies, postings, letters, resumes, registration, queries);
    }

    @Test
    void rejectsMissingTypeAndUnauthorizedActor() {
        assertError(() -> service.reindex("1", null, 10L), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        when(authorization.requireAdmin("2")).thenThrow(
                new CatalogException(HttpStatus.FORBIDDEN, "FORBIDDEN", "관리자 권한 필요"));
        assertError(() -> service.reindex("2", RagSourceType.COMPANY, 10L), HttpStatus.FORBIDDEN, "FORBIDDEN");
        verifyNoInteractions(companies, postings, letters, resumes, users, registration, queries);
    }

    private CoverLetter prepareLetter() {
        CoverLetter letter = identified(CoverLetter.create(owner, "이전 제목"), 12L);
        when(letters.findOwnedId(12L)).thenReturn(Optional.of(2L));
        when(users.findByIdForUpdate(2L)).thenReturn(Optional.of(owner));
        when(letters.findOwnedForUpdate(12L, 2L)).thenReturn(Optional.of(letter));
        return letter;
    }

    private Resume prepareResume() {
        Resume resume = identified(Resume.create(owner, "이력서", "file.pdf", "storage-key",
                "application/pdf", 100, "a".repeat(64)), 13L);
        when(resumes.findOwnedId(13L)).thenReturn(Optional.of(2L));
        when(users.findByIdForUpdate(2L)).thenReturn(Optional.of(owner));
        when(resumes.findOwnedForUpdate(13L, 2L)).thenReturn(Optional.of(resume));
        return resume;
    }

    private void prepareRegistration(RagSourceType type, long id) {
        when(registration.registerUpsert(any(), eq(3))).thenAnswer(invocation -> identified(
                RagIndexJobEntity.upsert(invocation.getArgument(0), 7, 3, Instant.EPOCH), 20L));
        when(queries.findJob(20L)).thenReturn(Optional.of(new AdminRagResponse.Job(
                20, type, id, 7, RagIndexOperation.UPSERT, RagIndexJobStatus.PENDING,
                0, 3, 0, null, null, null, Instant.EPOCH, Instant.EPOCH)));
    }

    private RagIndexTarget registeredTarget() {
        ArgumentCaptor<RagIndexTarget> captor = ArgumentCaptor.forClass(RagIndexTarget.class);
        verify(registration).registerUpsert(captor.capture(), eq(3));
        assertThat(captor.getValue().pipelineVersion()).isEqualTo("rag-v1");
        return captor.getValue();
    }

    private <T> T identified(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private void assertError(Runnable action, HttpStatus status, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CatalogException.class, exception -> {
            assertThat(exception.getStatus()).isEqualTo(status);
            assertThat(exception.getCode()).isEqualTo(code);
        });
    }
}
