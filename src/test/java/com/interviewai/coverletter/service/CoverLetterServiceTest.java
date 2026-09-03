package com.interviewai.coverletter.service;

import com.interviewai.auth.exception.InvalidAccessTokenException;
import com.interviewai.coverletter.dto.CoverLetterResponse;
import com.interviewai.coverletter.dto.CoverLetterSummaryResponse;
import com.interviewai.coverletter.dto.CreateCoverLetterRequest;
import com.interviewai.coverletter.dto.UpdateCoverLetterRequest;
import com.interviewai.coverletter.entity.CoverLetter;
import com.interviewai.coverletter.entity.CoverLetterRepresentative;
import com.interviewai.coverletter.entity.CoverLetterVersion;
import com.interviewai.coverletter.exception.CoverLetterNotFoundException;
import com.interviewai.coverletter.exception.CoverLetterVersionNotFoundException;
import com.interviewai.coverletter.exception.RepresentativeCoverLetterNotFoundException;
import com.interviewai.coverletter.repository.CoverLetterRepository;
import com.interviewai.coverletter.repository.CoverLetterRepresentativeRepository;
import com.interviewai.coverletter.repository.CoverLetterVersionRepository;
import com.interviewai.user.entity.User;
import com.interviewai.user.exception.UserNotFoundException;
import com.interviewai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoverLetterServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long COVER_LETTER_ID = 10L;
    private static final String TITLE = "백엔드 개발자 자기소개서";
    private static final String CONTENT = "지원 동기와 프로젝트 경험입니다.";

    @Mock
    private CoverLetterRepository coverLetterRepository;
    @Mock
    private CoverLetterVersionRepository versionRepository;
    @Mock
    private CoverLetterRepresentativeRepository representativeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private User user;
    @Mock
    private CoverLetter coverLetter;
    @Mock
    private CoverLetterVersion version;
    @Mock
    private CoverLetterRepresentative representative;

    private CoverLetterService coverLetterService;


    @BeforeEach
    void setUp() {
        coverLetterService = new CoverLetterService(
                coverLetterRepository,
                versionRepository,
                representativeRepository,
                userRepository
        );
    }


    @Test
    @DisplayName("자기소개서를 생성하면 첫 번째 버전을 함께 저장한다")
    void createsCoverLetterWithInitialVersion() {
        CreateCoverLetterRequest request = new CreateCoverLetterRequest(TITLE, CONTENT);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(coverLetterRepository.save(any(CoverLetter.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(versionRepository.save(any(CoverLetterVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CoverLetterResponse response = coverLetterService.create(USER_ID.toString(), request);

        ArgumentCaptor<CoverLetterVersion> captor = ArgumentCaptor.forClass(CoverLetterVersion.class);
        verify(versionRepository).save(captor.capture());
        assertThat(captor.getValue().getVersionNumber()).isEqualTo(1);
        assertThat(captor.getValue().getTitle()).isEqualTo(TITLE);
        assertThat(captor.getValue().getContent()).isEqualTo(CONTENT);
        assertThat(response.currentVersionNumber()).isEqualTo(1);
        assertThat(response.representative()).isFalse();
    }


    @Test
    @DisplayName("존재하지 않는 사용자는 자기소개서를 생성할 수 없다")
    void rejectsCreationForUnknownUser() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> coverLetterService.create(
                USER_ID.toString(), new CreateCoverLetterRequest(TITLE, CONTENT)
        )).isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(coverLetterRepository, versionRepository, representativeRepository);
    }


    @Test
    @DisplayName("목록은 대표 자기소개서를 구분해서 반환한다")
    void returnsSummariesWithRepresentativeFlag() {
        CoverLetter another = mock(CoverLetter.class);
        when(coverLetter.getId()).thenReturn(COVER_LETTER_ID);
        when(coverLetter.getTitle()).thenReturn(TITLE);
        when(coverLetter.getCurrentVersionNumber()).thenReturn(2);
        when(another.getId()).thenReturn(11L);
        when(another.getTitle()).thenReturn("다른 자기소개서");
        when(another.getCurrentVersionNumber()).thenReturn(1);
        when(representative.getCoverLetter()).thenReturn(coverLetter);
        when(representativeRepository.findById(USER_ID)).thenReturn(Optional.of(representative));
        when(coverLetterRepository.findAllByUser_IdOrderByUpdatedAtDesc(USER_ID))
                .thenReturn(List.of(coverLetter, another));

        List<CoverLetterSummaryResponse> responses = coverLetterService.getAll(USER_ID.toString());

        assertThat(responses).extracting(CoverLetterSummaryResponse::representative)
                .containsExactly(true, false);
    }


    @Test
    @DisplayName("자기소개서 상세는 현재 버전 본문을 반환한다")
    void returnsCurrentVersion() {
        stubOwnedCoverLetter();
        when(coverLetter.getId()).thenReturn(COVER_LETTER_ID);
        when(coverLetter.getTitle()).thenReturn(TITLE);
        when(coverLetter.getCurrentVersionNumber()).thenReturn(2);
        when(coverLetter.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 9, 3, 10, 0));
        when(coverLetter.getUpdatedAt()).thenReturn(LocalDateTime.of(2026, 9, 3, 11, 0));
        when(versionRepository.findByCoverLetter_IdAndVersionNumber(COVER_LETTER_ID, 2))
                .thenReturn(Optional.of(version));
        when(version.getContent()).thenReturn("현재 본문");
        when(representativeRepository.existsByUserIdAndCoverLetter_Id(USER_ID, COVER_LETTER_ID))
                .thenReturn(true);

        CoverLetterResponse response = coverLetterService.get(USER_ID.toString(), COVER_LETTER_ID);

        assertThat(response.content()).isEqualTo("현재 본문");
        assertThat(response.currentVersionNumber()).isEqualTo(2);
        assertThat(response.representative()).isTrue();
    }


    @Test
    @DisplayName("다른 사용자의 자기소개서는 존재하지 않는 것처럼 처리한다")
    void hidesOtherUsersCoverLetter() {
        when(coverLetterRepository.findByIdAndUser_Id(COVER_LETTER_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> coverLetterService.get(USER_ID.toString(), COVER_LETTER_ID))
                .isInstanceOf(CoverLetterNotFoundException.class);

        verifyNoInteractions(versionRepository);
    }


    @Test
    @DisplayName("자기소개서 수정은 비관적 잠금 후 새 버전을 저장한다")
    void updatesByCreatingNewVersion() {
        UpdateCoverLetterRequest request = new UpdateCoverLetterRequest("수정 제목", "수정 본문");
        when(coverLetterRepository.findOwnedForUpdate(COVER_LETTER_ID, USER_ID))
                .thenReturn(Optional.of(coverLetter));
        when(coverLetter.addVersion("수정 제목")).thenReturn(3);
        when(coverLetter.getCurrentVersionNumber()).thenReturn(3);
        when(versionRepository.save(any(CoverLetterVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CoverLetterResponse response = coverLetterService.update(
                USER_ID.toString(), COVER_LETTER_ID, request
        );

        ArgumentCaptor<CoverLetterVersion> captor = ArgumentCaptor.forClass(CoverLetterVersion.class);
        verify(versionRepository).save(captor.capture());
        assertThat(captor.getValue().getVersionNumber()).isEqualTo(3);
        assertThat(captor.getValue().getContent()).isEqualTo("수정 본문");
        assertThat(response.currentVersionNumber()).isEqualTo(3);
    }


    @Test
    @DisplayName("없는 버전은 조회할 수 없다")
    void rejectsUnknownVersion() {
        stubOwnedCoverLetter();
        when(versionRepository.findByCoverLetter_IdAndVersionNumber(COVER_LETTER_ID, 99))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> coverLetterService.getVersion(
                USER_ID.toString(), COVER_LETTER_ID, 99
        )).isInstanceOf(CoverLetterVersionNotFoundException.class);
    }


    @Test
    @DisplayName("과거 버전 복원은 원본을 변경하지 않고 새 버전을 생성한다")
    void restoresAsNewVersion() {
        when(coverLetterRepository.findOwnedForUpdate(COVER_LETTER_ID, USER_ID))
                .thenReturn(Optional.of(coverLetter));
        when(versionRepository.findByCoverLetter_IdAndVersionNumber(COVER_LETTER_ID, 1))
                .thenReturn(Optional.of(version));
        when(version.getTitle()).thenReturn("과거 제목");
        when(version.getContent()).thenReturn("과거 본문");
        when(coverLetter.addVersion("과거 제목")).thenReturn(4);
        when(coverLetter.getCurrentVersionNumber()).thenReturn(4);
        when(versionRepository.save(any(CoverLetterVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CoverLetterResponse response = coverLetterService.restore(
                USER_ID.toString(), COVER_LETTER_ID, 1
        );

        ArgumentCaptor<CoverLetterVersion> captor = ArgumentCaptor.forClass(CoverLetterVersion.class);
        verify(versionRepository).save(captor.capture());
        assertThat(captor.getValue().getVersionNumber()).isEqualTo(4);
        assertThat(captor.getValue().getTitle()).isEqualTo("과거 제목");
        assertThat(captor.getValue().getContent()).isEqualTo("과거 본문");
        assertThat(response.currentVersionNumber()).isEqualTo(4);
    }


    @Test
    @DisplayName("대표 자기소개서를 새로 설정한다")
    void setsRepresentative() {
        stubOwnedCoverLetter();
        when(representativeRepository.findById(USER_ID)).thenReturn(Optional.empty());

        coverLetterService.setRepresentative(USER_ID.toString(), COVER_LETTER_ID);

        verify(representativeRepository).save(any(CoverLetterRepresentative.class));
    }


    @Test
    @DisplayName("기존 대표 자기소개서를 다른 자기소개서로 교체한다")
    void changesRepresentative() {
        stubOwnedCoverLetter();
        when(representativeRepository.findById(USER_ID)).thenReturn(Optional.of(representative));

        coverLetterService.setRepresentative(USER_ID.toString(), COVER_LETTER_ID);

        verify(representative).changeCoverLetter(coverLetter);
        verify(representativeRepository, never()).save(any());
    }


    @Test
    @DisplayName("대표 자기소개서가 없으면 대표 조회에 실패한다")
    void rejectsMissingRepresentative() {
        when(representativeRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> coverLetterService.getRepresentative(USER_ID.toString()))
                .isInstanceOf(RepresentativeCoverLetterNotFoundException.class);
    }


    @Test
    @DisplayName("잘못된 JWT subject는 저장소를 조회하지 않고 거부한다")
    void rejectsInvalidSubject() {
        assertThatThrownBy(() -> coverLetterService.getAll("invalid-user-id"))
                .isInstanceOf(InvalidAccessTokenException.class);

        verifyNoInteractions(
                coverLetterRepository,
                versionRepository,
                representativeRepository,
                userRepository
        );
    }


    private void stubOwnedCoverLetter() {
        when(coverLetterRepository.findByIdAndUser_Id(COVER_LETTER_ID, USER_ID))
                .thenReturn(Optional.of(coverLetter));
    }
}
