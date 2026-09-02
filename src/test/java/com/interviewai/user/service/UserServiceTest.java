package com.interviewai.user.service;

import com.interviewai.auth.exception.InvalidAccessTokenException;
import com.interviewai.user.dto.CurrentUserResponse;
import com.interviewai.user.dto.UpdateUserRequest;
import com.interviewai.user.entity.User;
import com.interviewai.user.enums.AuthProvider;
import com.interviewai.user.enums.UserRole;
import com.interviewai.user.exception.UserNotFoundException;
import com.interviewai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long USER_ID = 1L;
    private static final String EMAIL = "user@example.com";
    private static final String NICKNAME = "테스트유저";

    @Mock
    private UserRepository userRepository;

    @Mock
    private User user;

    private UserService userService;


    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
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
}
