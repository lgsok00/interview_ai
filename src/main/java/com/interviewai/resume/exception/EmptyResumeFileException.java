package com.interviewai.resume.exception;

public class EmptyResumeFileException extends RuntimeException {

    public EmptyResumeFileException() {
        super("이력서 PDF 파일은 필수입니다.");
    }
}
