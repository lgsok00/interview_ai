package com.interviewai.global.error;

import com.interviewai.auth.exception.DuplicateEmailException;
import com.interviewai.auth.exception.InvalidAccessTokenException;
import com.interviewai.auth.exception.InvalidCredentialsException;
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
}
