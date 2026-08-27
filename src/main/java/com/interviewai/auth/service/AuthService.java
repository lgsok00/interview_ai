package com.interviewai.auth.service;

import com.interviewai.auth.dto.*;
import com.interviewai.auth.exception.DuplicateEmailException;
import com.interviewai.auth.exception.InvalidCredentialsException;
import com.interviewai.user.entity.User;
import com.interviewai.user.enums.AuthProvider;
import com.interviewai.user.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_users_email";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;


    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
    }


    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        // 빠르게 중복 여부를 알려주기 위한 사전 검사
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException();
        }

        User user = User.createLocalUser(
                normalizedEmail,
                passwordEncoder.encode(request.password()),
                request.nickname().trim()
        );

        try {
            /*
              save()만 호출하면 실제 INSERT가 트랜잭션 종료 시점까지 지연될 수 있다.
              saveAndFlush()를 사용해야 이 try 블록 안에서 DB 제약 조건 위반을 잡을 수 있다.
             */
            User savedUser = userRepository.saveAndFlush(user);

            return SignupResponse.from(savedUser);

        } catch (DataIntegrityViolationException exception) {
            if (isEmailUniqueConstraintViolation(exception)) {
                throw new DuplicateEmailException();
            }

            // 이메일 중복 이외의 DB 오류까지 중복 이메일로 처리하면 안 된다.
            throw exception;
        }
    }


    @Transactional
    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        User user = userRepository.findByEmail(normalizedEmail)
                .filter(candidate -> candidate.getProvider() == AuthProvider.LOCAL)
                .filter(candidate -> candidate.getPasswordHash() != null)
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);

        JwtTokenService.IssuedAccessToken accessToken = jwtTokenService.issueAccessToken(user);

        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user);

        return LoginResponse.bearer(
                accessToken.token(),
                refreshToken.token(),
                accessToken.expiresIn(),
                refreshToken.expiresIn()
        );
    }


    @Transactional
    public LoginResponse refresh(RefreshTokenRequest request) {
        RefreshTokenService.RotatedRefreshToken refreshToken = refreshTokenService.rotate(request.refreshToken());
        JwtTokenService.IssuedAccessToken accessToken = jwtTokenService.issueAccessToken(refreshToken.user());

        return LoginResponse.bearer(
                accessToken.token(),
                refreshToken.token(),
                accessToken.expiresIn(),
                refreshToken.expiresIn()
        );
    }


    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }


    private boolean isEmailUniqueConstraintViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                return EMAIL_UNIQUE_CONSTRAINT.equalsIgnoreCase(
                        violation.getConstraintName()
                );
            }

            cause = cause.getCause();
        }

        return false;
    }


    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
