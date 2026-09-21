package com.interviewai.resume.file;

import com.interviewai.resume.config.ResumeFileProperties;
import com.interviewai.resume.exception.EmptyResumeFileException;
import com.interviewai.resume.exception.ResumeFileTooLargeException;
import com.interviewai.resume.exception.UnsupportedResumeFileTypeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResumeUploadFileValidatorTest {

    private final ResumeUploadFileValidator validator = new ResumeUploadFileValidator(
            new ResumeFileProperties(DataSize.ofBytes(10), Path.of("resumes"))
    );


    @Test
    @DisplayName("PDF 파일명에서 클라이언트 경로를 제거한다")
    void normalizesFilename() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "C:\\fakepath\\resume.PDF", "application/pdf", "%PDF-".getBytes()
        );

        ValidatedResumeFile validated = validator.validate(file);

        assertThat(validated.originalFilename()).isEqualTo("resume.PDF");
        assertThat(validated.contentType()).isEqualTo("application/pdf");
    }


    @Test
    @DisplayName("빈 파일을 거부한다")
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", new byte[0]
        );

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(EmptyResumeFileException.class);
    }


    @Test
    @DisplayName("설정된 최대 크기를 초과한 파일을 거부한다")
    void rejectsOversizedFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", new byte[11]
        );

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(ResumeFileTooLargeException.class);
    }


    @Test
    @DisplayName("PDF가 아닌 확장자와 Content-Type을 거부한다")
    void rejectsUnsupportedType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.txt", "text/plain", "resume".getBytes()
        );

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(UnsupportedResumeFileTypeException.class);
    }
}
