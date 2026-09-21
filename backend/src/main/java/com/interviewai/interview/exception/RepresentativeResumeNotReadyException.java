package com.interviewai.interview.exception;

import com.interviewai.global.error.CatalogException;
import org.springframework.http.HttpStatus;

public class RepresentativeResumeNotReadyException extends CatalogException {

    public RepresentativeResumeNotReadyException() {
        super(
                HttpStatus.CONFLICT,
                "REPRESENTATIVE_RESUME_NOT_READY",
                "대표 이력서의 텍스트 추출이 완료되지 않았습니다."
        );
    }
}
