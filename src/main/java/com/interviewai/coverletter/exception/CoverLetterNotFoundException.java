package com.interviewai.coverletter.exception;

public class CoverLetterNotFoundException extends RuntimeException {

    public CoverLetterNotFoundException() {
        super("자기소개서를 찾을 수 없습니다.");
    }
}
