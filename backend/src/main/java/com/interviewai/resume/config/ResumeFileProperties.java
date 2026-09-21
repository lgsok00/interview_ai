package com.interviewai.resume.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DataSizeUnit;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

@Validated
@ConfigurationProperties(prefix = "resume.file")
public record ResumeFileProperties(
        @NotNull
        @DataSizeUnit(DataUnit.MEGABYTES)
        DataSize maxSize,

        @NotNull
        Path storageRoot
) {
}
