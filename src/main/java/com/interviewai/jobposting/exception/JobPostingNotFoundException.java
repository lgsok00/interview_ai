package com.interviewai.jobposting.exception;

import com.interviewai.global.error.CatalogException;
import org.springframework.http.HttpStatus;

public class JobPostingNotFoundException extends CatalogException {

    public JobPostingNotFoundException() {
        super(HttpStatus.NOT_FOUND, "JOB_POSTING_NOT_FOUND", "채용공고를 찾을 수 없습니다.");
    }
}
