package com.recipick.domain.payment.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

// 빌링키 원문의 저장용 암·복호화. Spring을 모르는 순수 클래스다(BillingKeyCryptoConfig가 키로 빈을 만든다).
// AES-256-GCM, 저장마다 새 IV(12바이트)·태그 128비트. 저장 형식은 "v1:<IV Base64>:<암호문+태그 Base64>"다 — 버전 접두어는
// 키 교체를 위한 자리이고 지금은 v1만 받는다. 어떤 실패도 메시지에 입력값(원문·저장값)을 넣지 않는다
public final class BillingKeyCipher {

    static final String VERSION = "v1";
    static final int KEY_BYTES = 32;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String SEPARATOR = ":";

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public BillingKeyCipher(SecretKey key) {
        if (key == null || key.getEncoded() == null || key.getEncoded().length != KEY_BYTES) {
            throw new IllegalArgumentException("빌링키 암호화 키는 " + KEY_BYTES + "바이트여야 합니다.");
        }
        // 설정에서 받은 키는 HMAC 알고리즘 이름을 달고 올 수 있어 AES 키로 다시 감싼다
        this.key = new SecretKeySpec(key.getEncoded(), "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("암호화할 빌링키가 없습니다.");
        }
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            Base64.Encoder encoder = Base64.getEncoder();
            return VERSION + SEPARATOR + encoder.encodeToString(iv) + SEPARATOR + encoder.encodeToString(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("빌링키를 암호화할 수 없습니다.", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            throw new IllegalStateException("저장된 빌링키 암호문이 없습니다.");
        }
        String[] parts = stored.split(SEPARATOR, -1);
        if (parts.length != 3) {
            throw new IllegalStateException("저장된 빌링키 암호문의 형식을 해석할 수 없습니다.");
        }
        if (!VERSION.equals(parts[0])) {
            throw new IllegalStateException("지원하지 않는 빌링키 암호화 버전입니다.");
        }
        byte[] iv;
        byte[] ciphertext;
        try {
            iv = Base64.getDecoder().decode(parts[1]);
            ciphertext = Base64.getDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            // Base64 예외 메시지에 입력 조각이 섞일 수 있어 cause로 넘기지 않는다
            throw new IllegalStateException("저장된 빌링키 암호문의 형식을 해석할 수 없습니다.");
        }
        if (iv.length != IV_BYTES) {
            throw new IllegalStateException("저장된 빌링키 암호문의 형식을 해석할 수 없습니다.");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // 태그 불일치(변조·다른 키)도 여기로 온다. JDK 메시지에는 입력값이 없다
            throw new IllegalStateException("빌링키를 복호화할 수 없습니다.", e);
        }
    }
}
