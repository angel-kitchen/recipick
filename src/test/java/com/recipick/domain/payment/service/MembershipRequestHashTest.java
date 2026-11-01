package com.recipick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.domain.member.dto.MembershipConsent;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 바로 구독 요청 해시. 동의 금액·문구 버전만 들어가고 동의 시각은 들어가지 않는다
class MembershipRequestHashTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 6, 10, 0);

    @Test
    @DisplayName("같은 금액·문구 버전이면 동의 시각이 달라도 같은 해시다(재시도가 COMMON-011이 되지 않는다)")
    void consentTimeDoesNotChangeHash() {
        String first = MembershipRequestHash.of(new MembershipConsent(3_900L, "v1", AT));
        String retry = MembershipRequestHash.of(new MembershipConsent(3_900L, "v1", AT.plusMinutes(5)));

        assertThat(first).hasSize(64).matches("^[0-9a-f]{64}$");
        assertThat(retry).isEqualTo(first);
    }

    @Test
    @DisplayName("금액이나 문구 버전이 다르면 다른 해시다")
    void amountOrVersionChangesHash() {
        String base = MembershipRequestHash.of(new MembershipConsent(3_900L, "v1", AT));

        assertThat(MembershipRequestHash.of(new MembershipConsent(3_800L, "v1", AT))).isNotEqualTo(base);
        assertThat(MembershipRequestHash.of(new MembershipConsent(3_900L, "v2", AT))).isNotEqualTo(base);
    }

    @Test
    @DisplayName("값이 비어 있어도 계산되고, 동의 객체가 없으면 예외다")
    void nullFieldsAndNullConsent() {
        assertThat(MembershipRequestHash.of(new MembershipConsent(null, null, null))).hasSize(64);
        assertThatThrownBy(() -> MembershipRequestHash.of(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
