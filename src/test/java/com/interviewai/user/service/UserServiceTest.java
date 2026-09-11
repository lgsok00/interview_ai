package com.interviewai.user.service;

import com.interviewai.auth.exception.InvalidAccessTokenException;
import com.interviewai.auth.service.RefreshTokenService;
import com.interviewai.coverletter.entity.CoverLetter;
import com.interviewai.coverletter.repository.CoverLetterRepository;
import com.interviewai.rag.document.RagSourceType;
import com.interviewai.rag.service.RagSourceChangeRegistrationService;
import com.interviewai.resume.entity.Resume;
import com.interviewai.resume.repository.ResumeRepository;
import com.interviewai.resume.storage.ResumeFileTransactionCleanup;
import com.interviewai.user.dto.ChangePasswordRequest;
import com.interviewai.user.dto.CurrentUserResponse;
import com.interviewai.user.dto.UpdateUserRequest;
import com.interviewai.user.entity.User;
import com.interviewai.user.enums.AuthProvider;
import com.interviewai.user.enums.UserRole;
import com.interviewai.user.exception.InvalidCurrentPasswordException;
import com.interviewai.user.exception.PasswordChangeNotSupportedException;
import com.interviewai.user.exception.SamePasswordException;
import com.interviewai.user.exception.UserNotFoundException;
import com.interviewai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long USER_ID = 1L;
    private static final String EMAIL = "user@example.com";
    private static final String NICKNAME = "테스트유저";
    private static final String CURRENT_PASSWORD = "password123";
    private static final String NEW_PASSWORD = "new-password456";
    private static final String ENCODED_PASSWORD = "{bcrypt}encoded-password";
    private static final String ENCODED_NEW_PASSWORD = "{bcrypt}encoded-new-password";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private CoverLetterRepository coverLetterRepository;

    @Mock
    private ResumeRepository resumeRepository;

    @Mock
    private RagSourceChangeRegistrationService ragRegistrationService;

    @Mock
    private ResumeFileTransactionCleanup resumeFileCleanup;

    @Mock
    private User user;

    @Mock
    private CoverLetter firstCoverLetter;

    @Mock
    private CoverLetter secondCoverLetter;

    @Mock
    private Resume firstResume;

    @Mock
    private Resume secondResume;

    private UserService userService;


    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                passwordEncoder,
                refreshTokenService,
                coverLetterRepository,
                resumeRepository,
                ragRegistrationService,
                resumeFileCleanup
        );
    }


    @Nested
    class GetCurrentUser {

        @Test
        @DisplayName("JWT subject에 해당하는 사용자를 조회한다")
        void returnsCurrentUser() {
            when(userRepository.findById(USER_ID))
                    .thenReturn(Optional.of(user));

            when(user.getId()).thenReturn(USER_ID);
            when(user.getEmail()).thenReturn(EMAIL);
            when(user.getNickname()).thenReturn(NICKNAME);
            when(user.getProvider()).thenReturn(AuthProvider.LOCAL);
            when(user.getRole()).thenReturn(UserRole.USER);

            CurrentUserResponse response =
                    userService.getCurrentUser(USER_ID.toString());

            assertThat(response).isEqualTo(
                    new CurrentUserResponse(
                            USER_ID,
                            EMAIL,
                            NICKNAME,
                            AuthProvider.LOCAL,
                            UserRole.USER
                    )
            );

            verify(userRepository).findById(USER_ID);
        }


        @Test
        @DisplayName("JWT subject에 해당하는 사용자가 없으면 조회에 실패한다")
        void rejectsUnknownUser() {
            when(userRepository.findById(USER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(
                    () -> userService.getCurrentUser(USER_ID.toString())
            ).isInstanceOf(UserNotFoundException.class);

            verify(userRepository).findById(USER_ID);
        }


        @Test
        @DisplayName("JWT subject가 숫자가 아니면 조회에 실패한다")
        void rejectsNonNumericSubject() {
            assertThatThrownBy(
                    () -> userService.getCurrentUser("invalid-user-id")
            ).isInstanceOf(InvalidAccessTokenException.class);

            verifyNoInteractions(userRepository);
        }


        @Test
        @DisplayName("JWT subject가 null이면 조회에 실패한다")
        void rejectsNullSubject() {
            assertThatThrownBy(
                    () -> userService.getCurrentUser(null)
            ).isInstanceOf(InvalidAccessTokenException.class);

            verifyNoInteractions(userRepository);
        }
    }


    @Nested
    class UpdateCurrentUser {

        @Test
        @DisplayName("JWT subject에 해당하는 사용자의 닉네임을 수정한다")
        void updatesNickname() {
            String updatedNickname = "수정된닉네임";
            UpdateUserRequest request = new UpdateUserRequest(updatedNickname);

            when(userRepository.findById(USER_ID))
                    .thenReturn(Optional.of(user));
            when(user.getId()).thenReturn(USER_ID);
            when(user.getEmail()).thenReturn(EMAIL);
            when(user.getNickname()).thenReturn(updatedNickname);
            when(user.getProvider()).thenReturn(AuthProvider.LOCAL);
            when(user.getRole()).thenReturn(UserRole.USER);

            CurrentUserResponse response = userService.updateCurrentUser(
                    USER_ID.toString(),
                    request
            );

            assertThat(response.nickname()).isEqualTo(updatedNickname);
            verify(userRepository).findById(USER_ID);
            verify(user).updateNickname(updatedNickname);
        }


        @Test
        @DisplayName("JWT subject에 해당하는 사용자가 없으면 수정에 실패한다")
        void rejectsUnknownUser() {
            UpdateUserRequest request = new UpdateUserRequest("수정닉네임");

            when(userRepository.findById(USER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.updateCurrentUser(
                    USER_ID.toString(),
                    request
            )).isInstanceOf(UserNotFoundException.class);

            verify(userRepository).findById(USER_ID);
            verifyNoInteractions(user);
        }


        @Test
        @DisplayName("JWT subject가 숫자가 아니면 수정에 실패한다")
        void rejectsNonNumericSubject() {
            UpdateUserRequest request = new UpdateUserRequest("수정닉네임");

            assertThatThrownBy(() -> userService.updateCurrentUser(
                    "invalid-user-id",
                    request
            )).isInstanceOf(InvalidAccessTokenException.class);

            verifyNoInteractions(userRepository, user);
        }
    }


    @Nested
    class ChangePassword {

        @Test
        @DisplayName("로컬 사용자의 비밀번호를 변경하고 모든 Refresh Token을 폐기한다")
        void changesPasswordAndRevokesRefreshTokens() {
            ChangePasswordRequest request = new ChangePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD);

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(user.getProvider()).thenReturn(AuthProvider.LOCAL);
            when(user.getPasswordHash()).thenReturn(ENCODED_PASSWORD);
            when(passwordEncoder.matches(CURRENT_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
            when(passwordEncoder.matches(NEW_PASSWORD, ENCODED_PASSWORD)).thenReturn(false);
            when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(ENCODED_NEW_PASSWORD);

            userService.changePassword(USER_ID.toString(), request);

            verify(user).changePassword(ENCODED_NEW_PASSWORD);
            verify(refreshTokenService).revokeAll(USER_ID);
        }


        @Test
        @DisplayName("현재 비밀번호가 일치하지 않으면 변경과 세션 폐기를 수행하지 않는다")
        void rejectsInvalidCurrentPassword() {
            ChangePasswordRequest request = new ChangePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD);

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(user.getProvider()).thenReturn(AuthProvider.LOCAL);
            when(user.getPasswordHash()).thenReturn(ENCODED_PASSWORD);
            when(passwordEncoder.matches(CURRENT_PASSWORD, ENCODED_PASSWORD)).thenReturn(false);

            assertThatThrownBy(() -> userService.changePassword(USER_ID.toString(), request))
                    .isInstanceOf(InvalidCurrentPasswordException.class);

            verify(user, never()).changePassword(anyString());
            verifyNoInteractions(refreshTokenService);
        }


        @Test
        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면 변경을 거부한다")
        void rejectsSamePassword() {
            ChangePasswordRequest request = new ChangePasswordRequest(CURRENT_PASSWORD, CURRENT_PASSWORD);

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(user.getProvider()).thenReturn(AuthProvider.LOCAL);
            when(user.getPasswordHash()).thenReturn(ENCODED_PASSWORD);
            when(passwordEncoder.matches(CURRENT_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

            assertThatThrownBy(() -> userService.changePassword(USER_ID.toString(), request))
                    .isInstanceOf(SamePasswordException.class);

            verify(passwordEncoder, never()).encode(anyString());
            verify(user, never()).changePassword(anyString());
            verifyNoInteractions(refreshTokenService);
        }


        @Test
        @DisplayName("OAuth2 사용자는 비밀번호를 변경할 수 없다")
        void rejectsOAuth2User() {
            ChangePasswordRequest request = new ChangePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD);

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(user.getProvider()).thenReturn(AuthProvider.GOOGLE);

            assertThatThrownBy(() -> userService.changePassword(USER_ID.toString(), request))
                    .isInstanceOf(PasswordChangeNotSupportedException.class);

            verifyNoInteractions(passwordEncoder, refreshTokenService);
            verify(user, never()).changePassword(anyString());
        }


        @Test
        @DisplayName("JWT subject에 해당하는 사용자가 없으면 비밀번호 변경에 실패한다")
        void rejectsUnknownUser() {
            ChangePasswordRequest request = new ChangePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.changePassword(USER_ID.toString(), request))
                    .isInstanceOf(UserNotFoundException.class);

            verifyNoInteractions(passwordEncoder, refreshTokenService, user);
        }


        @Test
        @DisplayName("JWT subject가 숫자가 아니면 비밀번호 변경에 실패한다")
        void rejectsNonNumericSubject() {
            ChangePasswordRequest request = new ChangePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD);

            assertThatThrownBy(() -> userService.changePassword("invalid-user-id", request))
                    .isInstanceOf(InvalidAccessTokenException.class);

            verifyNoInteractions(userRepository, passwordEncoder, refreshTokenService, user);
        }
    }


    @Nested
    class DeleteCurrentUser {

        @Test
        @DisplayName("개인 문서의 RAG DELETE를 등록하고 회원과 이력서 파일을 삭제한다")
        void registersDocumentDeletesAndDeletesCurrentUser() {
            when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
            when(coverLetterRepository.findAllOwnedForUpdate(USER_ID))
                    .thenReturn(List.of(firstCoverLetter, secondCoverLetter));
            when(resumeRepository.findAllOwnedForUpdate(USER_ID))
                    .thenReturn(List.of(firstResume, secondResume));
            when(firstCoverLetter.getId()).thenReturn(11L);
            when(secondCoverLetter.getId()).thenReturn(12L);
            when(firstResume.getId()).thenReturn(21L);
            when(secondResume.getId()).thenReturn(22L);
            when(firstResume.getStorageKey()).thenReturn("1/first.pdf");
            when(secondResume.getStorageKey()).thenReturn("1/second.pdf");

            userService.deleteCurrentUser(USER_ID.toString());

            InOrder order = inOrder(
                    userRepository,
                    coverLetterRepository,
                    resumeRepository,
                    ragRegistrationService,
                    resumeFileCleanup
            );
            order.verify(userRepository).findByIdForUpdate(USER_ID);
            order.verify(coverLetterRepository).findAllOwnedForUpdate(USER_ID);
            order.verify(resumeRepository).findAllOwnedForUpdate(USER_ID);
            order.verify(ragRegistrationService).registerDelete(RagSourceType.COVER_LETTER, 11L);
            order.verify(ragRegistrationService).registerDelete(RagSourceType.COVER_LETTER, 12L);
            order.verify(ragRegistrationService).registerDelete(RagSourceType.RESUME, 21L);
            order.verify(ragRegistrationService).registerDelete(RagSourceType.RESUME, 22L);
            order.verify(resumeFileCleanup).deleteAfterCommit("1/first.pdf");
            order.verify(resumeFileCleanup).deleteAfterCommit("1/second.pdf");
            order.verify(userRepository).delete(user);
            verifyNoInteractions(passwordEncoder, refreshTokenService);
        }


        @Test
        @DisplayName("개인 문서가 없어도 회원을 삭제한다")
        void deletesCurrentUserWithoutDocuments() {
            when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
            when(coverLetterRepository.findAllOwnedForUpdate(USER_ID)).thenReturn(List.of());
            when(resumeRepository.findAllOwnedForUpdate(USER_ID)).thenReturn(List.of());

            userService.deleteCurrentUser(USER_ID.toString());

            verify(userRepository).findByIdForUpdate(USER_ID);
            verify(coverLetterRepository).findAllOwnedForUpdate(USER_ID);
            verify(resumeRepository).findAllOwnedForUpdate(USER_ID);
            verify(userRepository).delete(user);
            verifyNoInteractions(ragRegistrationService, resumeFileCleanup);
        }


        @Test
        @DisplayName("RAG DELETE 등록에 실패하면 회원과 이력서 파일을 삭제하지 않는다")
        void preservesUserAndFilesWhenRagDeleteRegistrationFails() {
            when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
            when(coverLetterRepository.findAllOwnedForUpdate(USER_ID))
                    .thenReturn(List.of(firstCoverLetter));
            when(resumeRepository.findAllOwnedForUpdate(USER_ID))
                    .thenReturn(List.of(firstResume));
            when(firstCoverLetter.getId()).thenReturn(11L);
            when(firstResume.getId()).thenReturn(21L);
            doAnswer(invocation -> {
                if (invocation.getArgument(0) == RagSourceType.RESUME
                        && invocation.<Long>getArgument(1).equals(21L)) {
                    throw new IllegalStateException("RAG registration failed");
                }

                return null;
            }).when(ragRegistrationService).registerDelete(any(RagSourceType.class), anyLong());

            assertThatThrownBy(() -> userService.deleteCurrentUser(USER_ID.toString()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("RAG registration failed");

            verify(ragRegistrationService).registerDelete(RagSourceType.COVER_LETTER, 11L);
            verify(ragRegistrationService).registerDelete(RagSourceType.RESUME, 21L);
            verifyNoInteractions(resumeFileCleanup);
            verify(userRepository, never()).delete(any());
        }


        @Test
        @DisplayName("JWT subject에 해당하는 사용자가 없으면 탈퇴에 실패한다")
        void rejectsUnknownUser() {
            when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.deleteCurrentUser(USER_ID.toString()))
                    .isInstanceOf(UserNotFoundException.class);

            verify(userRepository).findByIdForUpdate(USER_ID);
            verify(userRepository, never()).delete(any());
            verifyNoInteractions(
                    passwordEncoder,
                    refreshTokenService,
                    coverLetterRepository,
                    resumeRepository,
                    ragRegistrationService,
                    resumeFileCleanup,
                    user
            );
        }


        @Test
        @DisplayName("JWT subject가 숫자가 아니면 탈퇴에 실패한다")
        void rejectsNonNumericSubject() {
            assertThatThrownBy(() -> userService.deleteCurrentUser("invalid-user-id"))
                    .isInstanceOf(InvalidAccessTokenException.class);

            verifyNoInteractions(
                    userRepository,
                    passwordEncoder,
                    refreshTokenService,
                    coverLetterRepository,
                    resumeRepository,
                    ragRegistrationService,
                    resumeFileCleanup,
                    user
            );
        }
    }
}
