package com.interviewai.ai.usage;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class AiUsageIdentity {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;


    public AiUsageIdentity(AiUsageProperties properties) {
        if (!properties.enabled()) {
            this.key = null;

            return;
        }

        byte[] decoded;

        try {
            decoded = Base64.getDecoder().decode(properties.hmacKey());

        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("AI_USAGE_HMAC_KEY는 Base64 형식이어야 합니다.");
        }

        if (decoded.length < 32) {
            throw new IllegalArgumentException("AI_USAGE_HMAC_KEY는 디코딩 후 32바이트 이상이어야 합니다.");
        }

        this.key = new SecretKeySpec(decoded, ALGORITHM);
    }


    public String subjectKey(String email) {
        if (key == null) {
            throw new IllegalStateException("비활성화된 AI 사용량 식별자를 사용할 수 없습니다.");
        }

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("이메일은 필수입니다.");
        }

        String normalized = email.strip().toLowerCase(Locale.ROOT);

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);

            byte[] digest = mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);

        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("AI 사용량 식별자를 생성하지 못했습니다.", exception);
        }
    }
}
