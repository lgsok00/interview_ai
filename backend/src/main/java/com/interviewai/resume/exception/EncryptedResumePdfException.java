package com.interviewai.resume.exception;

public class EncryptedResumePdfException extends RuntimeException {

    public EncryptedResumePdfException() {
        super("암호화된 PDF 파일은 사용할 수 없습니다.");
    }
}
