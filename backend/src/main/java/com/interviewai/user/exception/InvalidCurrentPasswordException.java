package com.interviewai.user.exception;

public class InvalidCurrentPasswordException extends RuntimeException {

    public InvalidCurrentPasswordException() {
        super("현재 비밀번호가 올바르지 않습니다.");
    }
}
