package com.interviewai.resume.service;

import com.interviewai.auth.exception.InvalidAccessTokenException;
import com.interviewai.resume.dto.ResumeResponse;
import com.interviewai.resume.dto.ResumeUploadRequest;
import com.interviewai.resume.entity.Resume;
import com.interviewai.resume.entity.ResumeRepresentative;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

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

    @Mock private ResumeRepository resumeRepository;
    @Mock private ResumeRepresentativeRepository representativeRepository;
    @Mock private UserRepository userRepository;
    @Mock private ResumeUploadFileValidator fileValidator;
    @Mock private ResumePdfProcessor pdfProcessor;
    @Mock private ResumeFileStorage fileStorage;
    @Mock private ResumeFileTransactionCleanup fileCleanup;
    @Mock private User user;
    @Mock private Resume resume;

    private ResumeService resumeService;
    private MockMultipartFile multipartFile;


    @BeforeEach
    void setUp() {
        resumeService = new ResumeService(
                resumeRepository, representativeRepository, userRepository,
                fileValidator, pdfProcessor, fileStorage, fileCleanup
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
        when(resumeRepository.save(any(Resume.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResumeResponse response = resumeService.create(
                USER_ID.toString(), new ResumeUploadRequest("이력서"), multipartFile
        );

        verify(fileCleanup).deleteAfterRollback("1/new.pdf");
        verify(resumeRepository).save(any(Resume.class));
        assertThat(response.extractionStatus().name()).isEqualTo("COMPLETED");
        assertThat(response.extractedText()).isEqualTo("resume text");
    }


    @Test
    @DisplayName("존재하지 않는 사용자는 파일 검증과 저장 전에 거부한다")
    void rejectsUnknownUser() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resumeService.create(
                USER_ID.toString(), new ResumeUploadRequest("이력서"), multipartFile
        )).isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(fileValidator, pdfProcessor, fileStorage, fileCleanup);
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
    }


    @Test
    @DisplayName("이력서 삭제는 DB 삭제 후 파일 삭제를 예약한다")
    void deletesResumeAndSchedulesFileCleanup() {
        when(resumeRepository.findByIdAndUser_Id(RESUME_ID, USER_ID)).thenReturn(Optional.of(resume));
        when(resume.getStorageKey()).thenReturn("1/resume.pdf");

        resumeService.delete(USER_ID.toString(), RESUME_ID);

        verify(resumeRepository).delete(resume);
        verify(fileCleanup).deleteAfterCommit("1/resume.pdf");
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
