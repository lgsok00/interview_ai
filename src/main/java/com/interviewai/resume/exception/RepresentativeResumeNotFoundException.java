package com.interviewai.resume.exception;

public class RepresentativeResumeNotFoundException extends RuntimeException {

    public RepresentativeResumeNotFoundException() {
        super("대표 이력서가 설정되지 않았습니다.");
    }
}
