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
    private final CoverLetterRepository coverLetterRepository;
    private final ResumeRepository resumeRepository;
    private final RagSourceChangeRegistrationService ragRegistrationService;
    private final ResumeFileTransactionCleanup resumeFileCleanup;


    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            CoverLetterRepository coverLetterRepository,
            ResumeRepository resumeRepository,
            RagSourceChangeRegistrationService ragRegistrationService,
            ResumeFileTransactionCleanup resumeFileCleanup
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.coverLetterRepository = coverLetterRepository;
        this.resumeRepository = resumeRepository;
        this.ragRegistrationService = ragRegistrationService;
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

        User user = userRepository.findByIdForUpdate(userId).orElseThrow(UserNotFoundException::new);

        List<CoverLetter> coverLetters = coverLetterRepository.findAllOwnedForUpdate(userId);

        List<Resume> resumes = resumeRepository.findAllOwnedForUpdate(userId);

        coverLetters.forEach(coverLetter ->
                ragRegistrationService.registerDelete(RagSourceType.COVER_LETTER, coverLetter.getId())
        );

        resumes.forEach(resume -> ragRegistrationService.registerDelete(RagSourceType.RESUME, resume.getId()));

        resumes.stream()
                .map(Resume::getStorageKey)
                .forEach(resumeFileCleanup::deleteAfterCommit);

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
