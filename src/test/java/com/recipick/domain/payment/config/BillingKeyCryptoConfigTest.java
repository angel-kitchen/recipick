package com.recipick.domain.payment.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.domain.payment.service.BillingKeyCipher;
import com.recipick.domain.payment.service.BillingKeyFingerprint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

// TrialBlockConfigTest와 같은 방식 — 설정 클래스의 메서드를 MockEnvironment로 직접 부른다
class BillingKeyCryptoConfigTest {

    private static final String KEY = "billing-key-test-0001";

    @TempDir
    Path directory;

    private final BillingKeyCryptoConfig config = new BillingKeyCryptoConfig();

    @Test
    @DisplayName("설정값이 있으면 그 키로 암·복호화하고 지문을 만든다")
    void usesConfiguredSecrets() throws Exception {
        // given
        String encryptionSecret = base64(32);
        String hmacSecret = base64(48);
        MockEnvironment environment = new MockEnvironment()
            .withProperty(BillingKeyCryptoConfig.ENCRYPTION_SECRET, encryptionSecret)
            .withProperty(BillingKeyCryptoConfig.HMAC_SECRET, hmacSecret);
        environment.setActiveProfiles("prod");

        // when
        BillingKeyCipher cipher = config.billingKeyCipher(environment);
        BillingKeyFingerprint fingerprint = config.billingKeyFingerprint(environment);

        // then — 같은 설정으로 만든 다른 인스턴스와 호환된다
        assertThat(config.billingKeyCipher(environment).decrypt(cipher.encrypt(KEY))).isEqualTo(KEY);
        assertThat(config.billingKeyFingerprint(environment).of(KEY)).isEqualTo(fingerprint.of(KEY));
    }

    @Test
    @DisplayName("로컬 외 환경에서 키가 없으면 기동을 거절한다")
    void requiresSecretsOutsideLocal() {
        // given
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        // when, then
        assertThatThrownBy(() -> config.billingKeyCipher(environment))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("BILLING_KEY_ENCRYPTION_SECRET");
        assertThatThrownBy(() -> config.billingKeyFingerprint(environment))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("BILLING_KEY_HMAC_SECRET");
    }

    @Test
    @DisplayName("local과 다른 프로필이 함께 켜져 있으면 키 자동 생성을 거절한다")
    void rejectsLocalFallbackWithOtherProfile() {
        // given
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local", "prod");

        // when, then
        assertThatThrownBy(() -> config.billingKeyCipher(environment)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("로컬은 키 파일을 자동 생성하고 재시작 뒤에도 같은 키를 쓴다")
    void createsAndReusesLocalKeyFiles() throws Exception {
        // given
        Path encryptionPath = directory.resolve("keys/billing-key-encryption-secret");
        Path hmacPath = directory.resolve("keys/billing-key-hmac-secret");
        MockEnvironment environment = new MockEnvironment()
            .withProperty(BillingKeyCryptoConfig.ENCRYPTION_LOCAL_KEY_PATH, encryptionPath.toString())
            .withProperty(BillingKeyCryptoConfig.HMAC_LOCAL_KEY_PATH, hmacPath.toString());
        environment.setActiveProfiles("local");

        // when
        String stored = config.billingKeyCipher(environment).encrypt(KEY);
        String hash = config.billingKeyFingerprint(environment).of(KEY);

        // then
        assertThat(Files.exists(encryptionPath)).isTrue();
        assertThat(Files.exists(hmacPath)).isTrue();
        assertThat(Files.readString(encryptionPath)).isNotEqualTo(Files.readString(hmacPath));
        assertThat(config.billingKeyCipher(environment).decrypt(stored)).isEqualTo(KEY);
        assertThat(config.billingKeyFingerprint(environment).of(KEY)).isEqualTo(hash);
    }

    @Test
    @DisplayName("기본 로컬 키 파일 이름은 JWT·체험 차단 키와 겹치지 않는다")
    void defaultLocalFileNamesAreDistinct() {
        assertThat(BillingKeyCryptoConfig.ENCRYPTION_LOCAL_FILE).isEqualTo("billing-key-encryption-secret");
        assertThat(BillingKeyCryptoConfig.HMAC_LOCAL_FILE).isEqualTo("billing-key-hmac-secret");
    }

    @Test
    @DisplayName("암호화 키가 32바이트가 아니거나 지문 키가 32바이트 미만이면 기동하지 않는다")
    void rejectsWeakSecrets() {
        // given
        MockEnvironment shortEncryption = new MockEnvironment()
            .withProperty(BillingKeyCryptoConfig.ENCRYPTION_SECRET, base64(16));
        MockEnvironment longEncryption = new MockEnvironment()
            .withProperty(BillingKeyCryptoConfig.ENCRYPTION_SECRET, base64(48));
        MockEnvironment shortHmac = new MockEnvironment()
            .withProperty(BillingKeyCryptoConfig.HMAC_SECRET, base64(16));

        // when, then
        assertThatThrownBy(() -> config.billingKeyCipher(shortEncryption))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.billingKeyCipher(longEncryption))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.billingKeyFingerprint(shortHmac))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Base64가 아닌 설정값은 거부한다")
    void rejectsInvalidBase64() {
        // given
        MockEnvironment environment = new MockEnvironment()
            .withProperty(BillingKeyCryptoConfig.ENCRYPTION_SECRET, "%%%")
            .withProperty(BillingKeyCryptoConfig.HMAC_SECRET, "%%%");

        // when, then
        assertThatThrownBy(() -> config.billingKeyCipher(environment)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> config.billingKeyFingerprint(environment)).isInstanceOf(RuntimeException.class);
    }

    private static String base64(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
