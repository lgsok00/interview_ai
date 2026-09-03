package com.interviewai.coverletter.exception;

public class CoverLetterVersionNotFoundException extends RuntimeException {

    public CoverLetterVersionNotFoundException() {
        super("자기소개서 버전을 찾을 수 없습니다.");
    }
}
