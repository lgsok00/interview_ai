package com.interviewai.user.service;

import com.interviewai.auth.exception.InvalidAccessTokenException;
import com.interviewai.auth.service.RefreshTokenService;
import com.interviewai.resume.repository.ResumeRepository;
import com.interviewai.resume.storage.ResumeFileTransactionCleanup;
import com.interviewai.user.dto.ChangePasswordRequest;
import com.interviewai.user.dto.CurrentUserResponse;
import com.interviewai.user.dto.UpdateUserRequest;
import com.interviewai.user.entity.User;
import com.interviewai.user.enums.AuthProvider;
import com.interviewai.user.exception.InvalidCurrentPasswordException;
import com.interviewai.user.exception.PasswordChangeNotSupportedException;
import com.interviewai.user.exception.SamePasswordException;
import com.interviewai.user.exception.UserNotFoundException;
import com.interviewai.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final ResumeRepository resumeRepository;
    private final ResumeFileTransactionCleanup resumeFileCleanup;


    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            ResumeRepository resumeRepository,
            ResumeFileTransactionCleanup resumeFileCleanup
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.resumeRepository = resumeRepository;
        this.resumeFileCleanup = resumeFileCleanup;
    }


    public CurrentUserResponse getCurrentUser(String subject) {
        Long userId = parseUserId(subject);

        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);

        return CurrentUserResponse.from(user);
    }


    @Transactional
    public CurrentUserResponse updateCurrentUser(String subject, UpdateUserRequest request) {
        Long userId = parseUserId(subject);

        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        user.updateNickname(request.nickname());

        return CurrentUserResponse.from(user);
    }


    @Transactional
    public void changePassword(String subject, ChangePasswordRequest request) {
        Long userId = parseUserId(subject);

        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);

        if (user.getProvider() != AuthProvider.LOCAL) {
            throw new PasswordChangeNotSupportedException();
        }

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException();
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new SamePasswordException();
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        refreshTokenService.revokeAll(userId);
    }


    @Transactional
    public void deleteCurrentUser(String subject) {
        Long userId = parseUserId(subject);

        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);

        List<String> resumeStorageKeys = resumeRepository.findStorageKeysByUserId(userId);

        resumeStorageKeys.forEach(resumeFileCleanup::deleteAfterCommit);

        userRepository.delete(user);
    }


    private Long parseUserId(String subject) {
        try {
            return Long.valueOf(subject);

        } catch (NumberFormatException exception) {
            throw new InvalidAccessTokenException();
        }
    }
}
