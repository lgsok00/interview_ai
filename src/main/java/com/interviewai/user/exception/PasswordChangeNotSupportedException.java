package com.interviewai.user.exception;

public class PasswordChangeNotSupportedException extends RuntimeException {

    public PasswordChangeNotSupportedException() {
        super("로컬 계정만 비밀번호를 변경할 수 있습니다.");
    }
}
