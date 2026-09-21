package com.interviewai.auth.exception;

public class OAuth2EmailConflictException extends RuntimeException {

    public OAuth2EmailConflictException() {
        super("해당 이메일로 가입된 다른 인증 방식의 계정이 존재합니다.");
    }
}
