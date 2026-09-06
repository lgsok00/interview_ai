package com.interviewai.company.exception;

import com.interviewai.global.error.CatalogException;
import org.springframework.http.HttpStatus;

public class CompanyHasJobPostingsException extends CatalogException {

    public CompanyHasJobPostingsException() {
        super(HttpStatus.CONFLICT, "COMPANY_HAS_JOB_POSTINGS", "채용공고가 등록된 기업은 삭제할 수 없습니다.");
    }
}
