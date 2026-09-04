package com.interviewai.global.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

@Getter
public class CatalogException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, String> errors;


    public CatalogException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }


    private CatalogException(HttpStatus status, String code, String message, Map<String, String> errors) {
        super(message);
        this.status = status;
        this.code = code;
        this.errors = Map.copyOf(errors);
    }


    public static CatalogException invalid(String field, String message) {
        return new CatalogException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "요청 값이 올바르지 않습니다.",
                Map.of(field, message)
        );
    }
}
