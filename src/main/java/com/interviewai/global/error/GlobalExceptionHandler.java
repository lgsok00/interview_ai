package com.interviewai.global.error;

import com.interviewai.auth.exception.DuplicateEmailException;
import com.interviewai.auth.exception.InvalidAccessTokenException;
import com.interviewai.auth.exception.InvalidCredentialsException;
import com.interviewai.auth.exception.InvalidRefreshTokenException;
import com.interviewai.coverletter.exception.CoverLetterNotFoundException;
import com.interviewai.coverletter.exception.CoverLetterVersionNotFoundException;
import com.interviewai.coverletter.exception.RepresentativeCoverLetterNotFoundException;
import com.interviewai.user.exception.InvalidCurrentPasswordException;
import com.interviewai.user.exception.PasswordChangeNotSupportedException;
import com.interviewai.user.exception.SamePasswordException;
import com.interviewai.user.exception.UserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateEmailException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDuplicateEmail(DuplicateEmailException exception) {
        return ErrorResponse.of("DUPLICATE_EMAIL", exception.getMessage());
    }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.putIfAbsent(error.getField(), error.getDefaultMessage())
                );

        return ErrorResponse.of("VALIDATION_ERROR", "요청 값이 올바르지 않습니다.", errors);
    }


    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleInvalidCredentials(InvalidCredentialsException exception) {
        return ErrorResponse.of("INVALID_CREDENTIALS", exception.getMessage());
    }


    @ExceptionHandler(InvalidAccessTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleInvalidAccessToken(InvalidAccessTokenException exception) {
        return ErrorResponse.of("INVALID_ACCESS_TOKEN", exception.getMessage());
    }


    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleUserNotFound(UserNotFoundException exception) {
        return ErrorResponse.of("USER_NOT_FOUND", exception.getMessage());
    }


    @ExceptionHandler(InvalidRefreshTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleInvalidRefreshToken(InvalidRefreshTokenException exception) {
        return ErrorResponse.of("INVALID_REFRESH_TOKEN", exception.getMessage());
    }


    @ExceptionHandler(InvalidCurrentPasswordException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleInvalidCurrentPassword(InvalidCurrentPasswordException exception) {
        return ErrorResponse.of("INVALID_CURRENT_PASSWORD", exception.getMessage());
    }


    @ExceptionHandler(PasswordChangeNotSupportedException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handlePasswordChangeNotSupported(PasswordChangeNotSupportedException exception) {
        return ErrorResponse.of("PASSWORD_CHANGE_NOT_SUPPORTED", exception.getMessage());
    }


    @ExceptionHandler(SamePasswordException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleSamePassword(SamePasswordException exception) {
        return ErrorResponse.of("SAME_PASSWORD", exception.getMessage());
    }


    @ExceptionHandler(CoverLetterNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleCoverLetterNotFound(CoverLetterNotFoundException exception) {
        return ErrorResponse.of("COVER_LETTER_NOT_FOUND", exception.getMessage());
    }


    @ExceptionHandler(CoverLetterVersionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleCoverLetterVersionNotFound(CoverLetterVersionNotFoundException exception) {
        return ErrorResponse.of("COVER_LETTER_VERSION_NOT_FOUND", exception.getMessage());
    }


    @ExceptionHandler(RepresentativeCoverLetterNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleRepresentativeCoverLetterNotFound(RepresentativeCoverLetterNotFoundException exception) {
        return ErrorResponse.of("REPRESENTATIVE_COVER_LETTER_NOT_FOUND", exception.getMessage());
    }
}
