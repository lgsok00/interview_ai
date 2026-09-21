package com.interviewai.global.validation;

import com.interviewai.global.error.CatalogException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public final class CatalogInput {

    private CatalogInput() {

    }


    public static String text(String value, String field, int max, boolean required) {
        String result = value == null ? null : value.strip();

        if (result == null || result.isBlank()) {
            if (required) {
                throw CatalogException.invalid(field, "필수 값입니다.");
            }

            return null;
        }

        if (result.length() > max) {
            throw CatalogException.invalid(field, max + "자 이하여야 합니다.");
        }

        return result;
    }


    public static String url(String value, String field) {
        String result = text(value, field, 2048, false);

        if (result == null) {
            return null;
        }

        try {
            URI uri = new URI(result);

            boolean httpScheme = "http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme());

            if (!httpScheme || uri.getHost() == null) {
                throw CatalogException.invalid(field, "호스트를 포함한 절대 HTTP/HTTPS 주소여야 합니다.");
            }

        } catch (URISyntaxException exception) {
            throw CatalogException.invalid(field, "올바른 URL이어야 합니다.");
        }

        return result;
    }


    public static Long id(Long value, String field) {
        if (value == null || value <= 0) {
            throw CatalogException.invalid(field, "양의 정수여야 합니다.");
        }

        return value;
    }


    public static PageRequest page(int page, int size) {
        if (page < 0) {
            throw CatalogException.invalid("page", "0 이상이어야 합니다.");
        }

        if (size < 1 || size > 100) {
            throw CatalogException.invalid("size", "1 이상 100 이하여야 합니다.");
        }

        return PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
    }


    public static String pattern(String keyword) {
        String value = text(keyword, "keyword", 100, false);

        if (value == null) {
            return "%";
        }

        String escaped = value
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");

        return "%" + escaped + "%";
    }


    public static LocalDateTime utc(OffsetDateTime value, String field) {
        if (value == null) {
            return null;
        }

        LocalDateTime result;

        try {
            result = value
                    .withOffsetSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime()
                    .truncatedTo(ChronoUnit.MICROS);

        } catch (DateTimeException exception) {
            throw CatalogException.invalid(field, "지원하는 날짜 범위를 벗어났습니다.");
        }

        if (result.getYear() < 1000 || result.getYear() > 9999) {
            throw CatalogException.invalid(field, "DB가 지원하는 날짜 범위를 벗어났습니다.");
        }

        return result;
    }


    public static void period(LocalDateTime opensAt, LocalDateTime closesAt) {
        if (opensAt != null
                && closesAt != null
                && !opensAt.isBefore(closesAt)) {
            throw CatalogException.invalid("closesAt", "시작  시각보다 늦어야 합니다.");
        }
    }
}
