package com.interviewai.resume.file;

public record ValidatedResumeFile(
        String originalFilename,
        String contentType,
        byte[] contents
) {

    public long size() {
        return contents.length;
    }
}
