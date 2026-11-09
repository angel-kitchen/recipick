package com.recipick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BillingKeyCipherTest {

    private static final String KEY = "billing-key-test-0001";

    private final BillingKeyCipher cipher = new BillingKeyCipher(newKey(32));

    @Test
    @DisplayName("암호화한 값을 같은 키로 복호화하면 원문이 돌아온다")
    void roundTrips() {
        // when
        String stored = cipher.encrypt(KEY);

        // then
        assertThat(cipher.decrypt(stored)).isEqualTo(KEY);
        assertThat(stored).startsWith("v1:").doesNotContain(KEY);
        assertThat(stored.split(":")).hasSize(3);
    }

    @Test
    @DisplayName("같은 원문을 두 번 암호화하면 IV가 달라 암호문도 다르다")
    void randomizesEachEncryption() {
        // when
        String first = cipher.encrypt(KEY);
        String second = cipher.encrypt(KEY);

        // then
        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo(cipher.decrypt(second));
    }

    @Test
    @DisplayName("다른 키로는 복호화되지 않고 예외 메시지에 암호문·원문이 없다")
    void rejectsOtherKey() {
        // given
        String stored = cipher.encrypt(KEY);
        BillingKeyCipher other = new BillingKeyCipher(newKey(32));

        // when
        Throwable thrown = catchThrowable(() -> other.decrypt(stored));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(messagesOf(thrown)).doesNotContain(KEY).doesNotContain(stored);
    }

    @Test
    @DisplayName("암호문 한 글자가 바뀌면(변조) 복호화를 거부한다")
    void rejectsTamperedCiphertext() {
        // given
        String stored = cipher.encrypt(KEY);
        String[] parts = stored.split(":");
        byte[] ciphertext = Base64.getDecoder().decode(parts[2]);
        ciphertext[0] ^= 0x01;
        String tampered = parts[0] + ":" + parts[1] + ":" + Base64.getEncoder().encodeToString(ciphertext);

        // when
        Throwable thrown = catchThrowable(() -> cipher.decrypt(tampered));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(messagesOf(thrown)).doesNotContain(KEY).doesNotContain(tampered);
    }

    @Test
    @DisplayName("모르는 버전 접두어는 거부한다 — 키 교체(v2)는 아직 없다")
    void rejectsUnknownVersion() {
        // given
        String stored = cipher.encrypt(KEY).replaceFirst("^v1:", "v2:");

        // when
        Throwable thrown = catchThrowable(() -> cipher.decrypt(stored));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class).hasMessageContaining("버전");
        assertThat(messagesOf(thrown)).doesNotContain(stored);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "v1", "v1:only-two", "v1:a:b:c", "v1:not-base64!:also-not!", "v1:AAAA:AAAA"})
    @DisplayName("형식이 맞지 않는 저장값은 거부하고 메시지에 입력값을 넣지 않는다")
    void rejectsMalformedStoredValue(String stored) {
        // when
        Throwable thrown = catchThrowable(() -> cipher.decrypt(stored));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        if (!stored.isEmpty()) {
            assertThat(messagesOf(thrown)).doesNotContain(stored);
        }
    }

    @Test
    @DisplayName("빈 원문은 암호화하지 않는다")
    void rejectsBlankPlaintext() {
        assertThatThrownBy(() -> cipher.encrypt(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cipher.encrypt(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {16, 24, 31, 33, 48})
    @DisplayName("32바이트가 아닌 키로는 만들 수 없다 — AES-256만 쓴다")
    void requiresExact256BitKey(int length) {
        assertThatThrownBy(() -> new BillingKeyCipher(newKey(length)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("HMAC 알고리즘 이름이 붙은 키 객체도 바이트가 32개면 받는다 — 설정 코드가 그런 키를 넘긴다")
    void acceptsHmacNamedKey() {
        // given
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        BillingKeyCipher fromHmacKey = new BillingKeyCipher(new SecretKeySpec(bytes, "HmacSHA256"));

        // then
        assertThat(fromHmacKey.decrypt(fromHmacKey.encrypt(KEY))).isEqualTo(KEY);
    }

    private static SecretKey newKey(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return new SecretKeySpec(bytes, "AES");
    }

    // 예외와 cause 체인의 메시지를 모두 모은다
    private static String messagesOf(Throwable thrown) {
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append('\n');
        }
        return messages.toString();
    }
}
