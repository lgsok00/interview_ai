package com.interviewai.coverletter.draft;

import com.interviewai.company.entity.Company;
import com.interviewai.coverletter.entity.CoverLetter;
import com.interviewai.coverletter.entity.CoverLetterVersion;
import com.interviewai.coverletter.repository.CoverLetterRepository;
import com.interviewai.coverletter.repository.CoverLetterVersionRepository;
import com.interviewai.interview.exception.RepresentativeResumeNotReadyException;
import com.interviewai.jobposting.entity.JobPosting;
import com.interviewai.jobposting.enums.EmploymentType;
import com.interviewai.jobposting.repository.JobPostingRepository;
import com.interviewai.resume.entity.Resume;
import com.interviewai.resume.entity.ResumeRepresentative;
import com.interviewai.resume.exception.ResumeNotFoundException;
import com.interviewai.resume.repository.ResumeRepository;
import com.interviewai.resume.repository.ResumeRepresentativeRepository;
import com.interviewai.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoverLetterDraftSnapshotAssemblerTest {

    @Mock
    private CoverLetterRepository letters;
    @Mock
    private CoverLetterVersionRepository versions;
    @Mock
    private JobPostingRepository jobs;
    @Mock
    private ResumeRepository resumes;
    @Mock
    private ResumeRepresentativeRepository representatives;
    @Mock
    private JobPosting job;
    @Mock
    private Company company;

    private CoverLetter letter;
    private CoverLetterDraftSnapshotAssembler assembler;

    static Stream<Arguments> emptyContents() {
        return Stream.of(false, true).flatMap(explicit ->
                Stream.of(null, "", " \n\t ", "\u2003")
                        .map(text -> Arguments.of(explicit, text)));
    }

    static Stream<Arguments> unavailableStates() {
        return Stream.of(false, true).flatMap(explicit ->
                Stream.of("PENDING", "TEXT_EXTRACTION_EMPTY", "TEXT_EXTRACTION_FAILED")
                        .map(state -> Arguments.of(explicit, state)));
    }

    @BeforeEach
    void setUp() {
        User user = User.createLocalUser("test@example.com", "encoded", "사용자");
        letter = CoverLetter.create(user, "기존 자기소개서");
        ReflectionTestUtils.setField(letter, "id", 10L);
        when(letters.findOwnedForUpdate(10L, 1L)).thenReturn(Optional.of(letter));
        when(versions.findByCoverLetter_IdAndVersionNumber(10L, 1))
                .thenReturn(Optional.of(CoverLetterVersion.create(letter, 1, "기존 제목", "기존 본문")));
        when(jobs.findDetail(20L)).thenReturn(Optional.of(job));
        assembler = new CoverLetterDraftSnapshotAssembler(
                letters, versions, jobs, resumes, representatives, new CoverLetterDraftInputHasher());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("대표 또는 직접 선택한 사용 가능한 이력서의 본문을 입력 스냅샷에 저장한다")
    void snapshotsReadyResume(boolean explicit) {
        Resume resume = resume();
        resume.completeExtraction("사용 가능한 추출 본문");
        select(resume, explicit);
        when(job.getId()).thenReturn(20L);
        when(job.getCompany()).thenReturn(company);
        when(company.getId()).thenReturn(21L);
        when(company.getName()).thenReturn("지원 기업");
        when(job.getEmploymentType()).thenReturn(EmploymentType.FULL_TIME);

        var prepared = assembler.assemble(1L, 10L, request(explicit));

        assertThat(prepared.input().resumeId()).isEqualTo(30L);
        assertThat(prepared.input().resumeTitle()).isEqualTo("선택 이력서");
        assertThat(prepared.input().resumeContent()).isEqualTo("사용 가능한 추출 본문");
        assertThat(prepared.inputHash()).hasSize(64);
        if (explicit) {
            verifyNoInteractions(representatives);
        } else {
            verifyNoInteractions(resumes);
        }
    }

    @Test
    @DisplayName("대표 미설정이면 초안 입력을 조립하지 않고 HTTP 409 오류를 반환한다")
    void rejectsMissingRepresentative() {
        when(representatives.findDetailByUserId(1L)).thenReturn(Optional.empty());

        assertNotReady(false);
        verifyNoInteractions(resumes);
    }

    @ParameterizedTest(name = "explicit={0}, content={1}")
    @MethodSource("emptyContents")
    @DisplayName("기존 완료 상태의 빈 본문은 대표·직접 선택 모두 서버에서 거부한다")
    void rejectsLegacyEmptyResume(boolean explicit, String text) {
        Resume resume = resume();
        resume.completeExtraction(text);
        select(resume, explicit);

        assertNotReady(explicit);
    }

    @ParameterizedTest(name = "explicit={0}, state={1}")
    @MethodSource("unavailableStates")
    @DisplayName("추출 대기·텍스트 없음·추출 오류는 대표·직접 선택 모두 서버에서 거부한다")
    void rejectsUnavailableResume(boolean explicit, String state) {
        Resume resume = resume();
        if (!"PENDING".equals(state)) {
            resume.failExtraction(state);
        }
        select(resume, explicit);

        assertNotReady(explicit);
    }

    @Test
    @DisplayName("없는 이력서 또는 타인 이력서 직접 선택 시 대표로 대체하지 않는다")
    void rejectsUnownedExplicitResumeWithoutFallback() {
        when(resumes.findByIdAndUser_Id(30L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assembler.assemble(1L, 10L, request(true)))
                .isInstanceOf(ResumeNotFoundException.class);
        verifyNoInteractions(representatives);
    }

    private void assertNotReady(boolean explicit) {
        assertThatThrownBy(() -> assembler.assemble(1L, 10L, request(explicit)))
                .isInstanceOfSatisfying(RepresentativeResumeNotReadyException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.getCode()).isEqualTo("REPRESENTATIVE_RESUME_NOT_READY");
                });
        assertThat(letter.getCurrentVersionNumber()).isEqualTo(1);
        verifyNoInteractions(company);
    }

    private Resume resume() {
        Resume resume = Resume.create(null, "선택 이력서", "resume.pdf", "1/resume.pdf",
                "application/pdf", 1024, "a".repeat(64));
        ReflectionTestUtils.setField(resume, "id", 30L);
        return resume;
    }

    private void select(Resume resume, boolean explicit) {
        if (explicit) {
            when(resumes.findByIdAndUser_Id(30L, 1L)).thenReturn(Optional.of(resume));
        } else {
            when(representatives.findDetailByUserId(1L))
                    .thenReturn(Optional.of(ResumeRepresentative.create(1L, resume)));
        }
    }

    private CreateCoverLetterDraftRequest request(boolean explicit) {
        return new CreateCoverLetterDraftRequest(20L, explicit ? 30L : null, null);
    }
}
