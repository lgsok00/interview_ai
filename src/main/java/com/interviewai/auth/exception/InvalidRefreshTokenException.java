package com.interviewai.auth.exception;

public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Refresh Token이 올바르지 않거나 만료되었습니다.");
    }
}
