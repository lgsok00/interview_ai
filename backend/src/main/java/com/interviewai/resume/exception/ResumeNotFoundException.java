package com.interviewai.resume.exception;

public class ResumeNotFoundException extends RuntimeException {

    public ResumeNotFoundException() {
        super("이력서를 찾을 수 없습니다.");
    }
}
