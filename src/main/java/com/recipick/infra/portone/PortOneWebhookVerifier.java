package com.recipick.infra.portone;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;


//PortOne V2 웹훅 서명 검증 (Standard Webhooks). 공식 JVM WebhookVerifier와 같은 규칙으로 직접 구현한다 —

@Slf4j
@Component
public class PortOneWebhookVerifier {

    // 공식 verifier와 같은 허용 범위. 초 단위 정수로 비교한다 — 현재 시각의 밀리초는 버린다
    private static final long TIMESTAMP_TOLERANCE_SECONDS = 5 * 60L;

    private static final String SECRET_PREFIX = "whsec_";
    private static final String SIGNATURE_VERSION = "v1";
    private static final String SIGNATURE_DELIMITER = " ";
    private static final String VERSION_DELIMITER = ",";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    // 시크릿이 없으면 null. 앱은 뜨고 웹훅만 NOT_CONFIGURED로 거절한다
    private final byte[] secretKey;
    private final Clock clock;

    public PortOneWebhookVerifier(PortOneWebhookProperties properties, Clock clock) {
        this.secretKey = decodeSecret(properties.secret());
        this.clock = clock;
    }

    public PortOneWebhookVerification verify(
        String webhookId, String webhookTimestamp, String webhookSignature, String body) {
        if (secretKey == null) {
            return PortOneWebhookVerification.NOT_CONFIGURED;
        }
        if (!StringUtils.hasText(webhookId)
            || !StringUtils.hasText(webhookTimestamp)
            || !StringUtils.hasText(webhookSignature)
            || body == null) {
            return PortOneWebhookVerification.MISSING_HEADERS;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(webhookTimestamp);
        } catch (NumberFormatException e) {
            return PortOneWebhookVerification.INVALID_TIMESTAMP;
        }
        long now = clock.instant().getEpochSecond();
        if (timestamp < now - TIMESTAMP_TOLERANCE_SECONDS || timestamp > now + TIMESTAMP_TOLERANCE_SECONDS) {
            return PortOneWebhookVerification.TIMESTAMP_OUT_OF_RANGE;
        }

        // 서명 대상의 timestamp는 헤더 문자열이 아니라 파싱한 값이다 — 공식 verifier와 같다
        byte[] expected = sign(webhookId + "." + timestamp + "." + body);
        // 시크릿을 바꾸는 동안 PortOne이 옛 키·새 키 서명을 함께 보낼 수 있다. 하나만 맞으면 된다
        for (String candidate : webhookSignature.split(SIGNATURE_DELIMITER)) {
            if (matches(expected, candidate)) {
                return PortOneWebhookVerification.VALID;
            }
        }
        return PortOneWebhookVerification.INVALID_SIGNATURE;
    }

    private byte[] sign(String content) {
        try {
            // Mac은 스레드 안전하지 않아 호출마다 새로 만든다
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKey, HMAC_ALGORITHM));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("웹훅 서명을 계산할 수 없습니다.", e);
        }
    }

    // 후보는 {version},{signature} 꼴이다. v1이 아닌 버전과 Base64가 깨진 값은 건너뛴다.
    // 비교는 상수 시간으로 해 시간 차로 서명을 추측할 수 없게 한다
    private static boolean matches(byte[] expected, String candidate) {
        String[] parts = candidate.split(VERSION_DELIMITER, 3);
        if (parts.length < 2 || !SIGNATURE_VERSION.equals(parts[0])) {
            return false;
        }
        try {
            byte[] actual = Base64.getDecoder().decode(parts[1]);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // 잘못된 시크릿은 설정 실수라 기동 시점에 바로 알린다. 시크릿 값은 메시지·로그에 넣지 않는다
    private static byte[] decodeSecret(String secret) {
        if (!StringUtils.hasText(secret)) {
            log.warn("PortOne 웹훅 시크릿이 설정되지 않아 모든 웹훅을 거절합니다.");
            return null;
        }
        String encoded = secret.startsWith(SECRET_PREFIX) ? secret.substring(SECRET_PREFIX.length()) : secret;
        byte[] key;
        try {
            key = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("portone.webhook.secret이 Base64 형식이 아닙니다.");
        }
        if (key.length == 0) {
            throw new IllegalStateException("portone.webhook.secret이 비어 있습니다.");
        }
        return key;
    }
}
