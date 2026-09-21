package com.interviewai.coverletter.draft;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Component
public class CoverLetterDraftInputHasher {

    public String hash(CoverLetterDraft.InputSnapshot input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            update(digest, input.jobPostingId());
            update(digest, input.resumeId());
            update(digest, input.baseVersionNumber());
            update(digest, input.coverLetterTitle());
            update(digest, input.coverLetterContent());

            update(digest, input.companyId());
            update(digest, input.companyName());
            update(digest, input.companyIndustry());
            update(digest, input.companyDescription());
            update(digest, input.companyWebsiteUrl());
            update(digest, input.companyLocation());

            update(digest, input.jobPostingTitle());
            update(digest, input.jobRole());
            update(digest, input.employmentType());
            update(digest, input.jobPostingLocation());
            update(digest, input.jobPostingDescription());
            update(digest, input.jobPostingSourceUrl());
            update(digest, input.jobPostingOpensAt());
            update(digest, input.jobPostingClosesAt());

            update(digest, input.resumeTitle());
            update(digest, input.resumeContent());
            update(digest, input.instruction());

            return HexFormat.of().formatHex(digest.digest());

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 입력 해시를 생성할 수 없습니다.", exception);
        }
    }


    private void update(MessageDigest digest, Object value) {
        if (value == null) {
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(-1).array());

            return;
        }

        String normalized = value instanceof LocalDateTime dateTime
                ? dateTime.toString()
                : value.toString();

        byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8);

        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
