package com.interviewai.resume.exception;

public class ResumeStorageException extends RuntimeException {

    public ResumeStorageException() {
        super("이력서 파일을 처리할 수 없습니다.");
    }

    public ResumeStorageException(Throwable cause) {
        super("이력서 파일을 처리할 수 없습니다.", cause);
    }
}
