package com.interviewai.rag.service;

import com.interviewai.company.entity.Company;
import com.interviewai.coverletter.entity.CoverLetter;
import com.interviewai.coverletter.entity.CoverLetterVersion;
import com.interviewai.jobposting.entity.JobPosting;
import com.interviewai.rag.document.RagSourceKey;
import com.interviewai.rag.document.RagSourceResolution;
import com.interviewai.rag.document.RagSourceSnapshot;
import com.interviewai.rag.document.RagSourceSnapshotFactory;
import com.interviewai.rag.document.RagSourceStatus;
import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.index.RagIndexTarget;
import com.interviewai.resume.entity.Resume;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagSourceChangeRegistrationServiceTest {

    @Mock private RagSourceSnapshotFactory snapshotFactory;
    @Mock private RagIndexJobRegistrationService jobRegistrationService;
    @Mock private Company company;
    @Mock private JobPosting jobPosting;
    @Mock private CoverLetter coverLetter;
    @Mock private CoverLetterVersion coverLetterVersion;
    @Mock private Resume resume;

    private RagSourceChangeRegistrationService service;


    @BeforeEach
    void setUp() {
        service = new RagSourceChangeRegistrationService(snapshotFactory, jobRegistrationService);
    }


    @Test
    @DisplayName("기업 변경은 고정 파이프라인과 재시도 횟수로 UPSERT를 등록한다")
    void registersCompanyUpsert() {
        RagSourceSnapshot snapshot = snapshot(RagSourceType.COMPANY, 10L, null, 10L);
        when(snapshotFactory.fromCompany(company)).thenReturn(snapshot);

        service.registerCompanyUpsert(company);

        assertUpsert(snapshot);
    }


    @Test
    @DisplayName("채용공고 변경은 고정 파이프라인과 재시도 횟수로 UPSERT를 등록한다")
    void registersJobPostingUpsert() {
        RagSourceSnapshot snapshot = snapshot(RagSourceType.JOB_POSTING, 20L, null, 10L);
        when(snapshotFactory.fromJobPosting(jobPosting)).thenReturn(snapshot);

        service.registerJobPostingUpsert(jobPosting);

        assertUpsert(snapshot);
    }


    @Test
    @DisplayName("자기소개서 변경은 현재 버전 스냅샷으로 UPSERT를 등록한다")
    void registersCoverLetterUpsert() {
        RagSourceSnapshot snapshot = snapshot(RagSourceType.COVER_LETTER, 30L, 1L, null);
        when(snapshotFactory.fromCoverLetter(coverLetter, coverLetterVersion)).thenReturn(snapshot);

        service.registerCoverLetterUpsert(coverLetter, coverLetterVersion);

        assertUpsert(snapshot);
    }


    @Test
    @DisplayName("추출 완료 이력서는 UPSERT를 등록한다")
    void registersReadyResumeUpsert() {
        RagSourceSnapshot snapshot = snapshot(RagSourceType.RESUME, 40L, 1L, null);
        when(snapshotFactory.fromResume(resume)).thenReturn(RagSourceResolution.ready(snapshot));

        service.registerResumeChange(resume);

        assertUpsert(snapshot);
        verify(jobRegistrationService, never()).registerDelete(snapshot.sourceKey(), 3);
    }


    @Test
    @DisplayName("색인할 수 없는 이력서는 기존 벡터 제거를 위해 DELETE를 등록한다")
    void registersDeleteForUnavailableResume() {
        RagSourceKey key = new RagSourceKey(RagSourceType.RESUME, 40L);
        when(snapshotFactory.fromResume(resume))
                .thenReturn(RagSourceResolution.unavailable(key, RagSourceStatus.EXTRACTION_FAILED));

        service.registerResumeChange(resume);

        verify(jobRegistrationService).registerDelete(key, 3);
        verify(jobRegistrationService, never()).registerUpsert(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt());
    }


    @Test
    @DisplayName("원본 삭제는 해당 유형과 ID의 DELETE를 등록한다")
    void registersSourceDelete() {
        service.registerDelete(RagSourceType.JOB_POSTING, 20L);

        verify(jobRegistrationService)
                .registerDelete(new RagSourceKey(RagSourceType.JOB_POSTING, 20L), 3);
    }


    @Test
    @DisplayName("null 이력서는 등록하지 않는다")
    void rejectsNullResume() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.registerResumeChange(null))
                .withMessage("resume은 필수입니다.");

        verify(snapshotFactory, never()).fromResume(org.mockito.ArgumentMatchers.any());
    }


    private void assertUpsert(RagSourceSnapshot snapshot) {
        ArgumentCaptor<RagIndexTarget> captor = ArgumentCaptor.forClass(RagIndexTarget.class);

        verify(jobRegistrationService).registerUpsert(captor.capture(), org.mockito.ArgumentMatchers.eq(3));

        assertThat(captor.getValue().snapshot()).isEqualTo(snapshot);
        assertThat(captor.getValue().pipelineVersion()).isEqualTo("rag-v1");
    }


    private RagSourceSnapshot snapshot(
            RagSourceType type,
            long sourceId,
            Long ownerUserId,
            Long companyId
    ) {
        return new RagSourceSnapshot(
                new RagSourceKey(type, sourceId),
                ownerUserId,
                companyId,
                "제목",
                "본문",
                "revision"
        );
    }
}
