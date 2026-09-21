package com.interviewai.auth.exception;

public class InvalidOAuth2UserException extends RuntimeException {

    public InvalidOAuth2UserException() {
        super("Google 사용자 정보가 올바르지 않습니다.");
    }
}
