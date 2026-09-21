package com.interviewai.rag.document;

import com.interviewai.company.entity.Company;
import com.interviewai.coverletter.entity.CoverLetter;
import com.interviewai.coverletter.entity.CoverLetterVersion;
import com.interviewai.jobposting.entity.JobPosting;
import com.interviewai.resume.entity.Resume;
import com.interviewai.resume.enums.ResumeExtractionStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

@Component
public class RagSourceSnapshotFactory {

    private static final String REVISION_ALGORITHM = "SHA-256";


    public RagSourceSnapshot fromCompany(Company company) {
        Objects.requireNonNull(company, "company는 필수입니다.");

        RagSourceKey sourceKey = new RagSourceKey(RagSourceType.COMPANY, company.getId());

        String content = lines(
                field("기업명", company.getName()),
                field("산업", company.getIndustry()),
                field("위치", company.getLocation()),
                section("기업 설명", company.getDescription())
        );

        return createSnapshot(sourceKey, null, company.getId(), company.getName(), content);
    }


    public RagSourceSnapshot fromJobPosting(JobPosting jobPosting) {
        Objects.requireNonNull(jobPosting, "jobPosting은 필수입니다.");

        Company company = Objects.requireNonNull(jobPosting.getCompany(), "채용공고의 company는 필수입니다.");

        RagSourceKey sourceKey = new RagSourceKey(RagSourceType.JOB_POSTING, jobPosting.getId());

        String content = lines(
                field("기업명", company.getName()),
                field("채용 제목", jobPosting.getTitle()),
                field("직무", jobPosting.getJobRole()),
                field("고용 형태", jobPosting.getEmploymentType().name()),
                field("위치", jobPosting.getLocation()),
                section("채용 내용", jobPosting.getDescription())
        );

        return createSnapshot(sourceKey, null, company.getId(), jobPosting.getTitle(), content);
    }


    public RagSourceSnapshot fromCoverLetter(CoverLetter coverLetter, CoverLetterVersion currentVersion) {
        Objects.requireNonNull(coverLetter, "coverLetter는 필수입니다.");
        Objects.requireNonNull(currentVersion, "currentVersion은 필수입니다.");

        if (!coverLetter.getId().equals(currentVersion.getCoverLetter().getId())) {
            throw new IllegalArgumentException("자기소개서와 버전의 문서 ID가 일치해야 합니다.");
        }

        if (!coverLetter.getCurrentVersionNumber().equals(currentVersion.getVersionNumber())) {
            throw new IllegalArgumentException("현재 자기소개서 버전만 RAG 문서로 변환할 수 있습니다.");
        }

        RagSourceKey sourceKey = new RagSourceKey(RagSourceType.COVER_LETTER, coverLetter.getId());

        String content = lines(
                field("자기소개서 제목", currentVersion.getTitle()),
                field("버전", currentVersion.getVersionNumber().toString()),
                section("자기소개서 본문", currentVersion.getContent())
        );

        return createSnapshot(sourceKey, coverLetter.getUser().getId(), null, currentVersion.getTitle(), content);
    }


    public RagSourceResolution fromResume(Resume resume) {
        Objects.requireNonNull(resume, "resume은 필수입니다.");

        RagSourceKey sourceKey = new RagSourceKey(RagSourceType.RESUME, resume.getId());

        if (resume.getExtractionStatus() == ResumeExtractionStatus.PENDING) {
            return RagSourceResolution.unavailable(sourceKey, RagSourceStatus.PENDING);
        }

        if (resume.getExtractionStatus() == ResumeExtractionStatus.FAILED) {
            return RagSourceResolution.unavailable(sourceKey, RagSourceStatus.EXTRACTION_FAILED);
        }

        String extractedText = resume.getExtractedText();

        if (extractedText == null || extractedText.isBlank()) {
            return RagSourceResolution.unavailable(sourceKey, RagSourceStatus.EMPTY_CONTENT);
        }

        String content = lines(
                field("이력서 제목", resume.getTitle()),
                section("이력서 본문", extractedText)
        );

        RagSourceSnapshot snapshot = createSnapshot(sourceKey, resume.getUser().getId(), null, resume.getTitle(), content);

        return RagSourceResolution.ready(snapshot);
    }


    private String lines(String... values) {
        return String.join(
                "\n",
                java.util.Arrays.stream(values)
                        .filter(value -> !value.isBlank())
                        .toList()
        );
    }


    private String field(String label, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        return label + ": " + value.strip();
    }


    private String section(String label, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        return label + ":\n" + value.strip();
    }


    private RagSourceSnapshot createSnapshot(
            RagSourceKey sourceKey, Long ownerUserId, Long companyId, String title, String content
    ) {
        String revision = revisionOf(sourceKey, ownerUserId, companyId, title, content);

        return new RagSourceSnapshot(sourceKey, ownerUserId, companyId, title, content, revision);
    }


    private String revisionOf(RagSourceKey sourceKey, Long ownerUserId, Long companyId, String title, String content) {
        String canonicalValue = String.join(
                "\n",
                sourceKey.sourceType().name(),
                sourceKey.sourceId().toString(),
                ownerUserId == null ? "" : ownerUserId.toString(),
                companyId == null ? "" : companyId.toString(),
                title.strip(),
                content.strip()
        );

        try {
            MessageDigest digest = MessageDigest.getInstance(REVISION_ALGORITHM);

            return HexFormat.of().formatHex(digest.digest(canonicalValue.getBytes(StandardCharsets.UTF_8)));

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }
}
