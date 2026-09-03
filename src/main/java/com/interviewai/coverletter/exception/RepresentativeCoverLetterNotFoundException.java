package com.interviewai.coverletter.exception;

public class RepresentativeCoverLetterNotFoundException extends RuntimeException {

    public RepresentativeCoverLetterNotFoundException() {
        super("대표 자기소개서가 설정되지 않았습니다.");
    }
}
