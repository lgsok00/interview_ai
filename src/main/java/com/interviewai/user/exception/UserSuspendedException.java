package com.interviewai.user.exception;

public class UserSuspendedException extends RuntimeException {

    public UserSuspendedException() {
        super("정지된 사용자 계정입니다.");
    }
}
