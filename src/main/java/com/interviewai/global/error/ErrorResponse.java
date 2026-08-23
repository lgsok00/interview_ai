package com.interviewai.global.error;

import java.util.Map;

public record ErrorResponse(
        String code,
        String message,
        Map<String, String> errors
) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, Map.of());
    }


    public static ErrorResponse of(String code, String message, Map<String, String> errors) {
        return new ErrorResponse(code, message, errors);
    }
}
