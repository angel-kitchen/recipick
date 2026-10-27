package com.recipick.domain.member.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

public class TrialPhoneHasher {
    private final SecretKey key;

    public TrialPhoneHasher(SecretKey key) {
        this.key = key;
    }

    public String hash(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("휴대전화 번호가 필요합니다.");
        }
        String normalized = phone.replace("-", "").strip();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("체험 차단 해시를 생성할 수 없습니다.", e);
        }
    }
}
