package com.interviewai.auth.exception;

public class InvalidAccessTokenException extends RuntimeException {

    public InvalidAccessTokenException() {
        super("Access Token의 인증 정보가 올바르지 않습니다.");
    }
}
