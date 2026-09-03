package com.interviewai.resume.exception;

public class InvalidResumePdfException extends RuntimeException {

    public InvalidResumePdfException() {
        super("유효한 PDF 파일이 아닙니다.");
    }
}
