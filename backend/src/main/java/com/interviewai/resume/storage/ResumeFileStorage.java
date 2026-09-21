package com.interviewai.resume.storage;

public interface ResumeFileStorage {

    String store(Long userId, byte[] contents);

    byte[] read(String storageKey);

    void delete(String storageKey);
}
