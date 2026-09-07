package com.interviewai.global.validation;

import com.interviewai.global.error.CatalogException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.*;

class CatalogInputTest {

    @Test
    @DisplayName("문자열은 공백을 제거하고 최대 길이까지 허용한다")
    void acceptsTextBoundary() {
        assertThat(CatalogInput.text("  " + "가".repeat(100) + "  ", "name", 100, true))
                .isEqualTo("가".repeat(100));
        assertThatThrownBy(() -> CatalogInput.text("가".repeat(101), "name", 100, true))
                .isInstanceOfSatisfying(CatalogException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(e.getErrors()).containsKey("name");
                });
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n", "\u2003"})
    @DisplayName("빈 선택 문자열은 null이고 필수 문자열은 오류다")
    void handlesBlankText(String value) {
        assertThat(CatalogInput.text(value, "name", 100, false)).isNull();
        assertThatThrownBy(() -> CatalogInput.text(value, "name", 100, true))
                .isInstanceOf(CatalogException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com/path?q=1", "http://example.com", "HTTPS://example.com"})
    @DisplayName("절대 HTTP와 HTTPS URL을 허용한다")
    void acceptsHttpUrls(String value) {
        assertThat(CatalogInput.url(" " + value + " ", "url")).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/relative", "ftp://example.com", "javascript:alert(1)",
            "https:///path", "https://example.com/a b"})
    @DisplayName("상대 경로와 HTTP 이외 또는 손상된 URL을 거부한다")
    void rejectsInvalidUrls(String value) {
        assertThatThrownBy(() -> CatalogInput.url(value, "url")).isInstanceOf(CatalogException.class);
    }

    @Test
    @DisplayName("URL 최대 길이와 페이지 최소 최대 경계를 검사한다")
    void checksUrlAndPageBounds() {
        String prefix = "https://example.com/";
        String url = prefix + "a".repeat(2048 - prefix.length());
        assertThat(CatalogInput.url(url, "url")).hasSize(2048);
        assertThatThrownBy(() -> CatalogInput.url(url + "a", "url")).isInstanceOf(CatalogException.class);
        assertThat(CatalogInput.page(0, 1).getPageSize()).isEqualTo(1);
        assertThat(CatalogInput.page(1, 100).getPageSize()).isEqualTo(100);
        assertThat(CatalogInput.page(0, 20).getSort().toString()).isEqualTo("createdAt: DESC,id: DESC");
        assertThatThrownBy(() -> CatalogInput.page(-1, 20)).isInstanceOf(CatalogException.class);
        assertThatThrownBy(() -> CatalogInput.page(0, 0)).isInstanceOf(CatalogException.class);
        assertThatThrownBy(() -> CatalogInput.page(0, 101)).isInstanceOf(CatalogException.class);
    }

    @Test
    @DisplayName("검색 특수문자를 escape하고 빈 검색과 검색어 길이를 처리한다")
    void escapesSearchPattern() {
        assertThat(CatalogInput.pattern(" !%_ ")).isEqualTo("%!!!%!_%");
        assertThat(CatalogInput.pattern(null)).isEqualTo("%");
        assertThat(CatalogInput.pattern("  ")).isEqualTo("%");
        assertThat(CatalogInput.pattern("가".repeat(100))).hasSize(102);
        assertThatThrownBy(() -> CatalogInput.pattern("가".repeat(101))).isInstanceOf(CatalogException.class);
    }

    @Test
    @DisplayName("시각은 동일 순간의 UTC로 변환하고 마이크로초 이하를 버린다")
    void normalizesUtcPrecision() {
        assertThat(CatalogInput.utc(OffsetDateTime.parse("2026-09-07T09:00:00.123456789+09:00"), "opensAt"))
                .isEqualTo(LocalDateTime.parse("2026-09-07T00:00:00.123456"));
        assertThat(CatalogInput.utc(null, "opensAt")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0999-12-31T23:59:59Z", "1000-01-01T00:00:00+09:00",
            "+10000-01-01T00:00:00Z", "9999-12-31T23:59:59-01:00"})
    @DisplayName("UTC 변환 결과가 MySQL 날짜 범위를 벗어나면 거부한다")
    void rejectsDatesOutsideDatabaseRange(String value) {
        assertThatThrownBy(() -> CatalogInput.utc(OffsetDateTime.parse(value), "opensAt"))
                .isInstanceOf(CatalogException.class);
    }

    @Test
    @DisplayName("기간은 시작보다 늦은 종료 또는 한쪽 생략만 허용한다")
    void validatesPeriod() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 7, 0, 0);
        assertThatCode(() -> CatalogInput.period(now, now.plusNanos(1000))).doesNotThrowAnyException();
        assertThatCode(() -> CatalogInput.period(null, now)).doesNotThrowAnyException();
        assertThatCode(() -> CatalogInput.period(now, null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> CatalogInput.period(now, now)).isInstanceOf(CatalogException.class);
        assertThatThrownBy(() -> CatalogInput.period(now, now.minusSeconds(1))).isInstanceOf(CatalogException.class);
    }
}
