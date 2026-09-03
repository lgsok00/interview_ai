package com.interviewai.resume.file;

import com.interviewai.resume.config.ResumeFileProperties;
import com.interviewai.resume.exception.EmptyResumeFileException;
import com.interviewai.resume.exception.ResumeFileTooLargeException;
import com.interviewai.resume.exception.ResumeStorageException;
import com.interviewai.resume.exception.UnsupportedResumeFileTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

@Component
public class ResumeUploadFileValidator {

    private static final int MAX_FILENAME_LENGTH = 255;

    private final ResumeFileProperties properties;


    public ResumeUploadFileValidator(ResumeFileProperties properties) {
        this.properties = properties;
    }


    public ValidatedResumeFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new EmptyResumeFileException();
        }

        if (file.getSize() > properties.maxSize().toBytes()) {
            throw new ResumeFileTooLargeException();
        }

        String originalFilename = normalizeFilename(file.getOriginalFilename());
        String contentType = file.getContentType();

        validateFilename(originalFilename);
        validateContentType(contentType);

        try {
            byte[] contents = file.getBytes();

            if (contents.length == 0) {
                throw new EmptyResumeFileException();
            }

            if (contents.length > properties.maxSize().toBytes()) {
                throw new ResumeFileTooLargeException();
            }

            return new ValidatedResumeFile(originalFilename, MediaType.APPLICATION_PDF_VALUE, contents);

        } catch (IOException exception) {
            throw new ResumeStorageException(exception);
        }
    }


    private String normalizeFilename(String originalFilename) {
        if (originalFilename == null) {
            throw new UnsupportedResumeFileTypeException();
        }

        String normalized = originalFilename.replace('\\', '/').trim();

        int separatorIndex = normalized.lastIndexOf('/');

        if (separatorIndex >= 0) {
            normalized = normalized.substring(separatorIndex + 1);
        }

        return normalized;
    }


    private void validateFilename(String filename) {
        if (filename.isBlank()
                || filename.length() > MAX_FILENAME_LENGTH
                || !filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new UnsupportedResumeFileTypeException();
        }
    }


    private void validateContentType(String contentType) {
        if (!MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(contentType)) {
            throw new UnsupportedResumeFileTypeException();
        }
    }
}
