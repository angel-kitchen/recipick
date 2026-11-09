package com.recipick.domain.payment.config;

import com.recipick.domain.payment.service.BillingKeyCipher;
import com.recipick.domain.payment.service.BillingKeyFingerprint;
import com.recipick.global.config.LocalJwtKeyStore;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.nio.file.Path;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

// 빌링키 저장 암호화 키·지문 키 바인딩. TrialBlockConfig와 같은 규칙이다 — 설정값(Base64)이 있으면 그 값을 쓰고, 비어 있으면
// local 단일 프로필에서만 ~/.recipick 아래 키 파일을 자동 생성하며 그 밖의 환경은 기동을 거부한다.
// 두 키는 분리한다: 암호화 키(정확히 32바이트)는 나중에 교체할 수 있지만 지문 키(32바이트 이상)는 바꾸면 기존 지문과 맞지 않는다
@Configuration
public class BillingKeyCryptoConfig {

    static final String ENCRYPTION_SECRET = "recipick.billing-key.encryption-secret";
    static final String ENCRYPTION_LOCAL_KEY_PATH = "recipick.billing-key.encryption-local-key-path";
    static final String HMAC_SECRET = "recipick.billing-key.hmac-secret";
    static final String HMAC_LOCAL_KEY_PATH = "recipick.billing-key.hmac-local-key-path";
    static final String ENCRYPTION_LOCAL_FILE = "billing-key-encryption-secret";
    static final String HMAC_LOCAL_FILE = "billing-key-hmac-secret";

    @Bean
    public BillingKeyCipher billingKeyCipher(Environment environment) throws IOException {
        String secret = environment.getProperty(ENCRYPTION_SECRET, "");
        if (!secret.isBlank()) {
            return new BillingKeyCipher(new SecretKeySpec(Decoders.BASE64.decode(secret), "AES"));
        }
        requireLocalProfile(environment, "BILLING_KEY_ENCRYPTION_SECRET");
        return new BillingKeyCipher(LocalJwtKeyStore.loadOrCreate(
            localKeyPath(environment, ENCRYPTION_LOCAL_KEY_PATH, ENCRYPTION_LOCAL_FILE)));
    }

    @Bean
    public BillingKeyFingerprint billingKeyFingerprint(Environment environment) throws IOException {
        String secret = environment.getProperty(HMAC_SECRET, "");
        if (!secret.isBlank()) {
            return new BillingKeyFingerprint(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)));
        }
        requireLocalProfile(environment, "BILLING_KEY_HMAC_SECRET");
        return new BillingKeyFingerprint(LocalJwtKeyStore.loadOrCreate(
            localKeyPath(environment, HMAC_LOCAL_KEY_PATH, HMAC_LOCAL_FILE)));
    }

    private static void requireLocalProfile(Environment environment, String variable) {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            profiles = environment.getDefaultProfiles();
        }
        if (profiles.length != 1 || !"local".equals(profiles[0])) {
            throw new IllegalStateException("로컬 외 환경에서는 " + variable + " 설정이 필요합니다.");
        }
    }

    private static Path localKeyPath(Environment environment, String property, String fileName) {
        return Path.of(environment.getProperty(property,
            Path.of(System.getProperty("user.home"), ".recipick", fileName).toString()));
    }
}
