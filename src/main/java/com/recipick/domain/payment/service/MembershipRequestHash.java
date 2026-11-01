package com.recipick.domain.payment.service;

import com.recipick.domain.member.dto.MembershipConsent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

// 바로 구독 요청의 멱등 해시. 기존 OrderCreateRequest.requestHash()와 같은 "길이:값" 이어붙이기 + SHA-256 hex.
// 동의 금액·문구 버전만 넣는다 — 동의 시각(autopayConsentedAt)은 재시도마다 달라질 수 있어 같은 요청이 COMMON-011이 되는 것을 막는다
public final class MembershipRequestHash {

    private MembershipRequestHash() {
    }

    public static String of(MembershipConsent consent) {
        if (consent == null) {
            throw new IllegalArgumentException("동의 정보가 필요합니다");
        }
        List<String> fields = List.of(
            consent.consentedAmount() == null ? "" : String.valueOf(consent.consentedAmount()),
            consent.consentTextVersion() == null ? "" : consent.consentTextVersion());
        StringBuilder canonical = new StringBuilder();
        for (String field : fields) {
            // 값 안에 구분자가 있어도 서로 다른 요청이 같은 문자열이 되지 않도록 길이를 앞에 붙인다
            canonical.append(field.length()).append(':').append(field);
        }
        return HexFormat.of().formatHex(sha256(canonical.toString()));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
