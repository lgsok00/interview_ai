package com.interviewai.company.exception;

import com.interviewai.global.error.CatalogException;
import org.springframework.http.HttpStatus;

public class CompanyNotFoundException extends CatalogException {

    public CompanyNotFoundException() {
        super(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "기업을 찾을 수 없습니다.");
    }
}
