package com.interviewai.resume.file;

public record ResumeDownload(
        String filename,
        String contentType,
        byte[] contents
) {

}
