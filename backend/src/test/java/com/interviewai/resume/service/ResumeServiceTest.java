package com.interviewai.resume.service;

import com.interviewai.auth.exception.InvalidAccessTokenException;
import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.service.RagSourceChangeRegistrationService;
import com.interviewai.resume.dto.ResumeResponse;
import com.interviewai.resume.dto.ResumeSummaryResponse;
import com.interviewai.resume.dto.ResumeUploadRequest;
import com.interviewai.resume.dto.UpdateResumeTitleRequest;
import com.interviewai.resume.entity.Resume;
import com.interviewai.resume.entity.ResumeRepresentative;
import com.interviewai.resume.enums.ResumeUsageStatus;
import com.interviewai.resume.exception.RepresentativeResumeNotFoundException;
import com.interviewai.resume.exception.ResumeNotFoundException;
import com.interviewai.resume.file.ResumePdfAnalysis;
import com.interviewai.resume.file.ResumePdfProcessor;
import com.interviewai.resume.file.ResumeUploadFileValidator;
import com.interviewai.resume.file.ValidatedResumeFile;
import com.interviewai.resume.repository.ResumeRepository;
import com.interviewai.resume.repository.ResumeRepresentativeRepository;
import com.interviewai.resume.storage.ResumeFileStorage;
import com.interviewai.resume.storage.ResumeFileTransactionCleanup;
import com.interviewai.user.entity.User;
import com.interviewai.user.exception.UserNotFoundException;
import com.interviewai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResumeServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long RESUME_ID = 10L;
    private static final byte[] PDF = "%PDF-test".getBytes();

    @Mock
    private ResumeRepository resumeRepository;
    @Mock
    private ResumeRepresentativeRepository representativeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RagSourceChangeRegistrationService ragRegistrationService;
    @Mock
    private ResumeUploadFileValidator fileValidator;
    @Mock
    private ResumePdfProcessor pdfProcessor;
    @Mock
    private ResumeFileStorage fileStorage;
    @Mock
    private ResumeFileTransactionCleanup fileCleanup;
    @Mock
    private User user;
    @Mock
    private Resume resume;

    private ResumeService resumeService;
    private MockMultipartFile multipartFile;


    @BeforeEach
    void setUp() {
        resumeService = new ResumeService(
                resumeRepository, representativeRepository, userRepository,
                ragRegistrationService, fileValidator, pdfProcessor, fileStorage, fileCleanup
        );
        multipartFile = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", PDF
        );
    }


    @Test
    @DisplayName("PDF를 저장하고 텍스트 추출이 완료된 이력서를 생성한다")
    void createsResume() {
        ValidatedResumeFile validated = new ValidatedResumeFile("resume.pdf", "application/pdf", PDF);
        ResumePdfAnalysis analysis = ResumePdfAnalysis.completed("a".repeat(64), "resume text");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(fileValidator.validate(multipartFile)).thenReturn(validated);
        when(pdfProcessor.analyze(PDF)).thenReturn(analysis);
        when(fileStorage.store(USER_ID, PDF)).thenReturn("1/new.pdf");
        when(resumeRepository.saveAndFlush(any(Resume.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResumeResponse response = resumeService.create(
                USER_ID.toString(), new ResumeUploadRequest("이력서"), multipartFile
        );

        verify(fileCleanup).deleteAfterRollback("1/new.pdf");
        ArgumentCaptor<Resume> captor = ArgumentCaptor.forClass(Resume.class);
        verify(resumeRepository).saveAndFlush(captor.capture());
        verify(ragRegistrationService).registerResumeChange(captor.getValue());
        assertThat(response.extractionStatus().name()).isEqualTo("COMPLETED");
        assertThat(response.usageStatus()).isEqualTo(ResumeUsageStatus.READY);
        assertThat(response.extractedText()).isEqualTo("resume text");
    }


    @ParameterizedTest
    @ValueSource(strings = {"TEXT_EXTRACTION_EMPTY", "TEXT_EXTRACTION_FAILED"})
    @DisplayName("텍스트 추출에 실패한 이력서도 RAG 상태 정리를 등록한다")
    void registersFailedExtractionResumeChange(String failureCode) {
        ValidatedResumeFile validated = new ValidatedResumeFile("resume.pdf", "application/pdf", PDF);
        ResumePdfAnalysis analysis = ResumePdfAnalysis.failed("a".repeat(64), failureCode);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(fileValidator.validate(multipartFile)).thenReturn(validated);
        when(pdfProcessor.analyze(PDF)).thenReturn(analysis);
        when(fileStorage.store(USER_ID, PDF)).thenReturn("1/new.pdf");
        when(resumeRepository.saveAndFlush(any(Resume.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResumeResponse response = resumeService.create(
                USER_ID.toString(), new ResumeUploadRequest("이력서"), multipartFile
        );

        ArgumentCaptor<Resume> captor = ArgumentCaptor.forClass(Resume.class);
        verify(resumeRepository).saveAndFlush(captor.capture());
        verify(ragRegistrationService).registerResumeChange(captor.getValue());
        assertThat(response.extractionStatus().name()).isEqualTo("FAILED");
        assertThat(response.extractionFailureCode()).isEqualTo(failureCode);
        assertThat(response.extractedText()).isNull();
        assertThat(response.usageStatus()).isEqualTo("TEXT_EXTRACTION_EMPTY".equals(failureCode)
                ? ResumeUsageStatus.EMPTY_TEXT : ResumeUsageStatus.EXTRACTION_FAILED);
        verify(fileStorage).store(USER_ID, PDF);
        verify(fileCleanup).deleteAfterRollback("1/new.pdf");
        verify(fileCleanup, never()).deleteAfterCommit(any());
    }

    @Test
    @DisplayName("목록은 기존 완료 상태의 빈 본문과 실제 사용 가능한 본문을 구분한다")
    void listsUsageForLegacyAndReadyResumes() {
        Resume legacy = realResume();
        legacy.completeExtraction(" \n\t ");
        Resume ready = realResume();
        ReflectionTestUtils.setField(ready, "id", 11L);
        ready.completeExtraction("사용 가능한 본문");
        when(representativeRepository.findById(USER_ID))
                .thenReturn(Optional.of(ResumeRepresentative.create(USER_ID, legacy)));
        when(resumeRepository.findAllByUser_IdOrderByUpdatedAtDesc(USER_ID))
                .thenReturn(List.of(legacy, ready));

        var responses = resumeService.getAll(USER_ID.toString());

        assertThat(responses).extracting(ResumeSummaryResponse::usageStatus)
                .containsExactly(ResumeUsageStatus.EMPTY_TEXT, ResumeUsageStatus.READY);
        assertThat(responses).extracting(ResumeSummaryResponse::representative).containsExactly(true, false);
        assertThat(legacy.getExtractionStatus().name()).isEqualTo("COMPLETED");
        verifyNoInteractions(ragRegistrationService, fileStorage);
    }

    @Test
    @DisplayName("텍스트 없는 PDF로 교체하면 기존 본문을 제거하고 새 실패 사유와 파일 정리를 등록한다")
    void replacesReadyResumeWithEmptyPdf() {
        Resume owned = realResume();
        owned.completeExtraction("이전 본문");
        when(resumeRepository.findOwnedForUpdate(RESUME_ID, USER_ID)).thenReturn(Optional.of(owned));
        when(fileValidator.validate(multipartFile))
                .thenReturn(new ValidatedResumeFile("empty.pdf", "application/pdf", PDF));
        when(pdfProcessor.analyze(PDF))
                .thenReturn(ResumePdfAnalysis.failed("b".repeat(64), "TEXT_EXTRACTION_EMPTY"));
        when(fileStorage.store(USER_ID, PDF)).thenReturn("1/new.pdf");
        when(representativeRepository.existsByUserIdAndResume_Id(USER_ID, RESUME_ID)).thenReturn(true);

        ResumeResponse response = resumeService.replaceFile(USER_ID.toString(), RESUME_ID, multipartFile);

        assertThat(response.extractedText()).isNull();
        assertThat(response.usageStatus()).isEqualTo(ResumeUsageStatus.EMPTY_TEXT);
        assertThat(response.extractionFailureCode()).isEqualTo("TEXT_EXTRACTION_EMPTY");
        assertThat(response.representative()).isTrue();
        assertThat(owned.getStorageKey()).isEqualTo("1/new.pdf");
        verify(fileCleanup).deleteAfterRollback("1/new.pdf");
        verify(fileCleanup).deleteAfterCommit("1/resume.pdf");
        verify(ragRegistrationService).registerResumeChange(owned);
    }

    @Test
    @DisplayName("본문 추출에 실패해도 소유자의 원본 PDF 다운로드는 가능하다")
    void downloadsEmptyExtractionPdf() {
        Resume owned = realResume();
        owned.failExtraction("TEXT_EXTRACTION_EMPTY");
        when(resumeRepository.findByIdAndUser_Id(RESUME_ID, USER_ID)).thenReturn(Optional.of(owned));
        when(fileStorage.read("1/resume.pdf")).thenReturn(PDF);

        var download = resumeService.download(USER_ID.toString(), RESUME_ID);

        assertThat(download.contents()).isEqualTo(PDF);
        assertThat(download.filename()).isEqualTo("resume.pdf");
        verifyNoInteractions(ragRegistrationService, fileCleanup);
    }

    private Resume realResume() {
        Resume owned = Resume.create(user, "이력서", "resume.pdf", "1/resume.pdf",
                "application/pdf", PDF.length, "a".repeat(64));
        ReflectionTestUtils.setField(owned, "id", RESUME_ID);
        return owned;
    }


    @Test
    @DisplayName("존재하지 않는 사용자는 파일 검증과 저장 전에 거부한다")
    void rejectsUnknownUser() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resumeService.create(
                USER_ID.toString(), new ResumeUploadRequest("이력서"), multipartFile
        )).isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(fileValidator, pdfProcessor, fileStorage, fileCleanup, ragRegistrationService);
    }


    @Test
    @DisplayName("다른 사용자의 이력서는 존재하지 않는 것처럼 처리한다")
    void hidesOtherUsersResume() {
        when(resumeRepository.findByIdAndUser_Id(RESUME_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resumeService.get(USER_ID.toString(), RESUME_ID))
                .isInstanceOf(ResumeNotFoundException.class);
    }


    @Test
    @DisplayName("PDF 교체 성공 시 기존 파일 삭제와 신규 파일 롤백 정리를 예약한다")
    void replacesFileTransactionally() {
        ValidatedResumeFile validated = new ValidatedResumeFile("new.pdf", "application/pdf", PDF);
        when(resumeRepository.findOwnedForUpdate(RESUME_ID, USER_ID)).thenReturn(Optional.of(resume));
        when(fileValidator.validate(multipartFile)).thenReturn(validated);
        when(pdfProcessor.analyze(PDF)).thenReturn(ResumePdfAnalysis.completed("b".repeat(64), "new text"));
        when(resume.getStorageKey()).thenReturn("1/old.pdf");
        when(fileStorage.store(USER_ID, PDF)).thenReturn("1/new.pdf");

        resumeService.replaceFile(USER_ID.toString(), RESUME_ID, multipartFile);

        verify(fileCleanup).deleteAfterRollback("1/new.pdf");
        verify(fileCleanup).deleteAfterCommit("1/old.pdf");
        verify(resume).replaceFile("new.pdf", "1/new.pdf", "application/pdf", PDF.length, "b".repeat(64));
        verify(resume).completeExtraction("new text");
        verify(ragRegistrationService).registerResumeChange(resume);
    }


    @Test
    @DisplayName("이력서 제목 수정은 잠근 원본의 변경 작업을 등록한다")
    void registersResumeChangeAfterTitleUpdate() {
        when(resumeRepository.findOwnedForUpdate(RESUME_ID, USER_ID)).thenReturn(Optional.of(resume));

        resumeService.updateTitle(
                USER_ID.toString(), RESUME_ID, new UpdateResumeTitleRequest("수정 이력서")
        );

        var inOrder = inOrder(resume, ragRegistrationService);
        inOrder.verify(resume).updateTitle("수정 이력서");
        inOrder.verify(ragRegistrationService).registerResumeChange(resume);
    }


    @Test
    @DisplayName("이력서 삭제는 DB 삭제 후 파일 삭제를 예약한다")
    void deletesResumeAndSchedulesFileCleanup() {
        when(resumeRepository.findOwnedForUpdate(RESUME_ID, USER_ID)).thenReturn(Optional.of(resume));
        when(resume.getStorageKey()).thenReturn("1/resume.pdf");

        resumeService.delete(USER_ID.toString(), RESUME_ID);

        var inOrder = inOrder(ragRegistrationService, resumeRepository, fileCleanup);
        inOrder.verify(ragRegistrationService).registerDelete(RagSourceType.RESUME, RESUME_ID);
        inOrder.verify(resumeRepository).delete(resume);
        inOrder.verify(fileCleanup).deleteAfterCommit("1/resume.pdf");
    }


    @Test
    @DisplayName("이력서 DELETE 작업 등록 실패 시 DB와 파일 삭제를 예약하지 않는다")
    void doesNotDeleteResumeWhenRegistrationFails() {
        when(resumeRepository.findOwnedForUpdate(RESUME_ID, USER_ID)).thenReturn(Optional.of(resume));
        when(resume.getStorageKey()).thenReturn("1/resume.pdf");
        doThrow(new IllegalStateException("registration failed"))
                .when(ragRegistrationService)
                .registerDelete(RagSourceType.RESUME, RESUME_ID);

        assertThatThrownBy(() -> resumeService.delete(USER_ID.toString(), RESUME_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("registration failed");

        verify(resumeRepository, never()).delete(any());
        verify(fileCleanup, never()).deleteAfterCommit(any());
    }


    @Test
    @DisplayName("대표 이력서를 새로 설정한다")
    void setsRepresentativeResume() {
        when(resumeRepository.findByIdAndUser_Id(RESUME_ID, USER_ID)).thenReturn(Optional.of(resume));
        when(representativeRepository.findById(USER_ID)).thenReturn(Optional.empty());

        resumeService.setRepresentative(USER_ID.toString(), RESUME_ID);

        verify(representativeRepository).save(any(ResumeRepresentative.class));
    }


    @Test
    @DisplayName("대표 이력서가 없으면 전용 예외를 반환한다")
    void rejectsMissingRepresentative() {
        when(representativeRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resumeService.getRepresentative(USER_ID.toString()))
                .isInstanceOf(RepresentativeResumeNotFoundException.class);
    }


    @Test
    @DisplayName("잘못된 JWT subject는 저장소 접근 전에 거부한다")
    void rejectsInvalidSubject() {
        assertThatThrownBy(() -> resumeService.getAll("invalid"))
                .isInstanceOf(InvalidAccessTokenException.class);

        verifyNoInteractions(resumeRepository, representativeRepository);
    }
}
