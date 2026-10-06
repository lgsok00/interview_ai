package com.interviewai.ai.usage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiUsageIdentityTest {

    @Test
    void matchesPublishedHmacSha256Vector() {
        // RFC 4231 case 2. Zero-padding a short HMAC key to 32 bytes preserves
        // its block-padded value and also satisfies the application key length policy.
        byte[] paddedKey = new byte[32];
        System.arraycopy("Jefe".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                0, paddedKey, 0, 4);
        String key = Base64.getEncoder().encodeToString(paddedKey);
        var identity = new AiUsageIdentity(properties(true, key));
        assertThat(identity.subjectKey("what do ya want for nothing?"))
                .isEqualTo("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
    }

    @Test
    void normalizesEmailAndPreservesSameIdentityAcrossInstances() {
        var identity = new AiUsageIdentity(properties(true, AiUsageConfigurationTest.TEST_KEY));
        String subject = identity.subjectKey("USER@example.com");
        assertThat(identity.subjectKey("  User@Example.COM  ")).isEqualTo(subject);
        assertThat(new AiUsageIdentity(properties(true, AiUsageConfigurationTest.TEST_KEY))
                .subjectKey("user@example.com")).isEqualTo(subject);
        assertThat(subject).matches("[0-9a-f]{64}").doesNotContain("user", "example.com");
    }

    @Test
    void distinguishesDifferentEmailsAndKeys() {
        var identity = new AiUsageIdentity(properties(true, AiUsageConfigurationTest.TEST_KEY));
        assertThat(identity.subjectKey("one@example.com"))
                .isNotEqualTo(identity.subjectKey("two@example.com"));
        byte[] other = new byte[32];
        other[0] = 1;
        var changedKey = new AiUsageIdentity(properties(true, Base64.getEncoder().encodeToString(other)));
        assertThat(changedKey.subjectKey("one@example.com"))
                .isNotEqualTo(identity.subjectKey("one@example.com"));
    }

    @Test
    void usesLocaleIndependentLowercase() {
        Locale previous = Locale.getDefault();
        try {
            var identity = new AiUsageIdentity(properties(true, AiUsageConfigurationTest.TEST_KEY));
            String expected = identity.subjectKey("i@example.com");
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(identity.subjectKey("I@EXAMPLE.COM")).isEqualTo(expected);
        } finally {
            Locale.setDefault(previous);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsMissingEmail(String email) {
        var identity = new AiUsageIdentity(properties(true, AiUsageConfigurationTest.TEST_KEY));
        assertThatThrownBy(() -> identity.subjectKey(email)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid!", "a2V5"})
    void rejectsMalformedOrShortKey(String key) {
        assertThatThrownBy(() -> new AiUsageIdentity(properties(true, key)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void disabledIdentityCannotBeUsed() {
        var identity = new AiUsageIdentity(properties(false, ""));
        assertThatThrownBy(() -> identity.subjectKey("user@example.com"))
                .isInstanceOf(IllegalStateException.class);
    }

    private AiUsageProperties properties(boolean enabled, String key) {
        return new AiUsageProperties(enabled, true, true, key,
                3, 15, 30, 3, 20, 2, 50, 500, 10, 90);
    }
}
