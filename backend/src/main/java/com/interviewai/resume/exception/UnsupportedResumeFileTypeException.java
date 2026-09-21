package com.interviewai.resume.exception;

public class UnsupportedResumeFileTypeException extends RuntimeException {

    public UnsupportedResumeFileTypeException() {
        super("PDF 형식의 이력서 파일만 사용할 수 있습니다.");
    }
}
