package com.interviewai.resume.storage;

import com.interviewai.resume.config.ResumeFileProperties;
import com.interviewai.resume.exception.ResumeStorageException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Component
public class LocalResumeFileStorage implements ResumeFileStorage {

    private final Path storageRoot;


    public LocalResumeFileStorage(ResumeFileProperties properties) {
        this.storageRoot = properties.storageRoot().toAbsolutePath().normalize();

        try {
            Files.createDirectories(storageRoot);

        } catch (IOException exception) {
            throw new ResumeStorageException(exception);
        }
    }


    @Override
    public String store(Long userId, byte[] contents) {
        String storageKey = userId + "/" + UUID.randomUUID() + ".pdf";
        Path target = resolveStorageKey(storageKey);

        try {
            Files.createDirectories(target.getParent());
            Files.write(target, contents, StandardOpenOption.CREATE_NEW);

            return storageKey;

        } catch (IOException exception) {
            throw new ResumeStorageException(exception);
        }
    }


    @Override
    public byte[] read(String storageKey) {
        Path target = resolveStorageKey(storageKey);

        try {
            return Files.readAllBytes(target);

        } catch (IOException exception) {
            throw new ResumeStorageException(exception);
        }
    }


    @Override
    public void delete(String storageKey) {
        Path target = resolveStorageKey(storageKey);

        try {
            Files.deleteIfExists(target);

        } catch (IOException exception) {
            throw new ResumeStorageException(exception);
        }
    }


    private Path resolveStorageKey(String storageKey) {
        Path resolved = storageRoot.resolve(storageKey).normalize();

        if (!resolved.startsWith(storageRoot)) {
            throw new ResumeStorageException();
        }

        return resolved;
    }
}
