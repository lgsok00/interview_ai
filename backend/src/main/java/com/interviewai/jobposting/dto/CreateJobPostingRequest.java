package com.interviewai.jobposting.dto;

import com.interviewai.jobposting.enums.EmploymentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record CreateJobPostingRequest(
        @NotNull(message = "기업 ID는 필수입니다.")
        @Positive(message = "기업 ID는 양의 정수여야 합니다.")
        Long companyId,

        @NotBlank(message = "공고 제목은 필수입니다.")
        @Size(max = 200, message = "공고 제목은 200자 이하여야 합니다.")
        String title,

        @NotBlank(message = "직무는 필수입니다.")
        @Size(max = 100, message = "직무는 100자 이하여야 합니다.")
        String jobRole,

        @NotNull(message = "고용 형태는 필수입니다.")
        EmploymentType employmentType,

        @Size(max = 200, message = "근무지는 200자 이하여야 합니다.")
        String location,

        @NotBlank(message = "공고 본문은 필수입니다.")
        @Size(max = 30000, message = "공고 본문은 30,000자 이하여야 합니다.")
        String description,

        @Size(max = 2048, message = "출처 URL은 2,048자 이하여야 합니다.")
        String sourceUrl,

        OffsetDateTime opensAt,

        OffsetDateTime closesAt,

        Boolean manuallyClosed
) {

    public boolean resolvedManuallyClosed() {
        return Boolean.TRUE.equals(manuallyClosed);
    }
}
