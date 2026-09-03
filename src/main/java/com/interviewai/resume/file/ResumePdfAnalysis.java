package com.interviewai.resume.file;

public record ResumePdfAnalysis(
        String sha256,
        String extractedText,
        String extractionFailureCode
) {

    public static ResumePdfAnalysis completed(String sha256, String extractedText) {
        return new ResumePdfAnalysis(sha256, extractedText, null);
    }


    public static ResumePdfAnalysis failed(String sha256, String failureCode) {
        return new ResumePdfAnalysis(sha256, null, failureCode);
    }


    public boolean extractionSucceeded() {
        return extractionFailureCode == null;
    }
}
