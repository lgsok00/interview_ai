package com.interviewai.resume.entity;

import com.interviewai.resume.dto.ResumeResponse;
import com.interviewai.resume.dto.ResumeSummaryResponse;
import com.interviewai.resume.enums.ResumeExtractionStatus;
import com.interviewai.resume.enums.ResumeUsageStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeUsageTest {

    @Test
    @DisplayName("신규 이력서는 본문 추출 전까지 사용 불가이며 API도 PENDING을 반환한다")
    void pendingResumeCannotBeUsed() {
        assertUsage(resume(), ResumeUsageStatus.PENDING);
    }

    @Test
    @DisplayName("추출 완료와 실제 본문이 모두 있는 이력서만 사용할 수 있다")
    void completedTextIsReady() {
        Resume resume = resume();
        resume.completeExtraction("백엔드 개발 경험");

        assertUsage(resume, ResumeUsageStatus.READY);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t\r ", "\u2003"})
    @DisplayName("기존 COMPLETED의 null·빈 값·공백 본문은 저장 상태 변경 없이 사용 불가다")
    void legacyCompletedEmptyContentIsUnavailable(String text) {
        Resume resume = resume();
        resume.completeExtraction(text);

        assertUsage(resume, ResumeUsageStatus.EMPTY_TEXT);
        assertThat(resume.getExtractionStatus()).isEqualTo(ResumeExtractionStatus.COMPLETED);
        assertThat(resume.getExtractedText()).isEqualTo(text);
        assertThat(resume.getExtractionFailureCode()).isNull();
    }

    @Test
    @DisplayName("텍스트 없음 실패 사유는 일반 추출 오류와 구분한다")
    void emptyExtractionFailureIsEmptyText() {
        Resume resume = resume();
        resume.failExtraction("TEXT_EXTRACTION_EMPTY");

        assertUsage(resume, ResumeUsageStatus.EMPTY_TEXT);
        assertThat(resume.getExtractionStatus()).isEqualTo(ResumeExtractionStatus.FAILED);
        assertThat(resume.getExtractedText()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"TEXT_EXTRACTION_FAILED", "OLD_UNKNOWN_FAILURE"})
    @DisplayName("추출 실패의 기존·알 수 없는 사유도 사용 불가로 안내한다")
    void otherExtractionFailuresAreUnavailable(String code) {
        Resume resume = resume();
        resume.failExtraction(code);

        assertUsage(resume, ResumeUsageStatus.EXTRACTION_FAILED);
    }

    @Test
    @DisplayName("파일을 교체하면 이전의 사용 가능 본문을 폐기하고 PENDING으로 돌아간다")
    void replacementResetsUsage() {
        Resume resume = resume();
        resume.completeExtraction("이전 본문");
        resume.replaceFile("new.pdf", "1/new.pdf", "application/pdf", 2048, "b".repeat(64));

        assertUsage(resume, ResumeUsageStatus.PENDING);
        assertThat(resume.getExtractedText()).isNull();
        assertThat(resume.getExtractionFailureCode()).isNull();
    }

    private Resume resume() {
        return Resume.create(null, "이력서", "resume.pdf", "1/resume.pdf",
                "application/pdf", 1024, "a".repeat(64));
    }

    private void assertUsage(Resume resume, ResumeUsageStatus expected) {
        assertThat(resume.getUsageStatus()).isEqualTo(expected);
        assertThat(ResumeSummaryResponse.of(resume, true).usageStatus()).isEqualTo(expected);
        assertThat(ResumeResponse.of(resume, true).usageStatus()).isEqualTo(expected);
    }
}
