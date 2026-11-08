package com.recipick.domain.payment.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

// 빌링키 원문의 지문. UNIQUE 제약과 동등 조회는 암호문이 아니라 이 값으로 한다 — 암호문은 저장마다 달라지기 때문이다.
// TrialPhoneHasher와 같은 HMAC-SHA256(hex 64자)이고 키는 암호화 키와 분리한다. 이 키를 바꾸면 기존 지문과 맞지 않는다
public final class BillingKeyFingerprint {

    static final int MIN_KEY_BYTES = 32;
    public static final int LENGTH = 64;

    private final SecretKey key;

    public BillingKeyFingerprint(SecretKey key) {
        if (key == null || key.getEncoded() == null || key.getEncoded().length < MIN_KEY_BYTES) {
            throw new IllegalArgumentException("빌링키 지문 키는 " + MIN_KEY_BYTES + "바이트 이상이어야 합니다.");
        }
        this.key = key;
    }

    public String of(String portoneBillingKey) {
        if (portoneBillingKey == null || portoneBillingKey.isBlank()) {
            throw new IllegalArgumentException("지문을 만들 빌링키가 없습니다.");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(portoneBillingKey.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("빌링키 지문을 만들 수 없습니다.", e);
        }
    }
}
