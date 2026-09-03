package com.interviewai.resume.storage;

import com.interviewai.resume.config.ResumeFileProperties;
import com.interviewai.resume.exception.ResumeStorageException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalResumeFileStorageTest {

    @TempDir
    private Path tempDirectory;


    @Test
    @DisplayName("사용자 디렉터리에 UUID 저장 키로 파일을 저장하고 읽고 삭제한다")
    void storesReadsAndDeletesFile() {
        LocalResumeFileStorage storage = storage();
        byte[] contents = "%PDF-test".getBytes();

        String storageKey = storage.store(7L, contents);

        assertThat(storageKey).startsWith("7/").endsWith(".pdf");
        assertThat(storage.read(storageKey)).isEqualTo(contents);
        assertThat(Files.exists(tempDirectory.resolve(storageKey))).isTrue();

        storage.delete(storageKey);

        assertThat(Files.exists(tempDirectory.resolve(storageKey))).isFalse();
    }


    @Test
    @DisplayName("저장 루트 밖으로 벗어나는 키 접근을 거부한다")
    void rejectsPathTraversal() {
        LocalResumeFileStorage storage = storage();

        assertThatThrownBy(() -> storage.read("../secret.pdf"))
                .isInstanceOf(ResumeStorageException.class);
    }


    private LocalResumeFileStorage storage() {
        return new LocalResumeFileStorage(
                new ResumeFileProperties(DataSize.ofMegabytes(10), tempDirectory)
        );
    }
}
