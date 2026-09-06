package com.interviewai.company.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCompanyRequest(
        @NotBlank(message = "기업명은 필수입니다.")
        @Size(max = 100, message = "기업명은 100자 이하여야 합니다.")
        String name,

        @Size(max = 100, message = "산업 분야는 100자 이하여야 합니다.")
        String industry,

        @NotBlank(message = "기업 소개는 필수입니다.")
        @Size(max = 20000, message = "기업 소개는 20,000자 이하여야 합니다.")
        String description,

        @Size(max = 2048, message = "홈페이지 URL은 2,048자 이하여야 합니다.")
        String websiteUrl,

        @Size(max = 200, message = "기업 위치는 200자 이하여야 합니다.")
        String location
) {
}
