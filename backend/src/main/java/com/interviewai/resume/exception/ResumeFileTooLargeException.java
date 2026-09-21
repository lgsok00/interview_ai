package com.interviewai.resume.exception;

public class ResumeFileTooLargeException extends RuntimeException {

    public ResumeFileTooLargeException() {
        super("이력서 PDF 파일은 10MB 이하여야 합니다.");
    }
}
