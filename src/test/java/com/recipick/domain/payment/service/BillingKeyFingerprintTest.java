package com.recipick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BillingKeyFingerprintTest {

    private static final String KEY = "billing-key-test-0001";

    private final BillingKeyFingerprint fingerprint = new BillingKeyFingerprint(newKey(32));

    @Test
    @DisplayName("같은 원문은 항상 같은 지문이고 hex 64자다")
    void isDeterministic() {
        // when
        String first = fingerprint.of(KEY);
        String second = fingerprint.of(KEY);

        // then
        assertThat(first).isEqualTo(second).hasSize(BillingKeyFingerprint.LENGTH).matches("[0-9a-f]{64}");
        assertThat(first).doesNotContain(KEY);
    }

    @Test
    @DisplayName("원문이 한 글자만 달라도, 키가 달라도 지문이 다르다")
    void differsByInputAndKey() {
        assertThat(fingerprint.of(KEY)).isNotEqualTo(fingerprint.of(KEY + "x"));
        assertThat(fingerprint.of(KEY)).isNotEqualTo(new BillingKeyFingerprint(newKey(32)).of(KEY));
    }

    @Test
    @DisplayName("빈 원문과 32바이트 미만 키는 거부한다")
    void rejectsBlankAndShortKey() {
        assertThatThrownBy(() -> fingerprint.of(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fingerprint.of(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BillingKeyFingerprint(newKey(31))).isInstanceOf(IllegalArgumentException.class);
    }

    private static SecretKey newKey(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
