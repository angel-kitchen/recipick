package com.recipick.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BillingKeyTest {

    private static final Long MEMBER_ID = 7L;
    private static final String KEY = "billing-key-test-0001";
    private static final LocalDateTime ISSUED_AT = LocalDateTime.of(2026, 10, 31, 14, 0);

    @Test
    @DisplayName("발급된 키로 만든 새 행은 ACTIVE이고 PortOne 발급 시각을 그대로 갖는다")
    void issued() {
        // when
        BillingKey key = BillingKey.issued(MEMBER_ID, KEY, ISSUED_AT);

        // then
        assertThat(key.getStatus()).isEqualTo(BillingKeyStatus.ACTIVE);
        assertThat(key.isActive()).isTrue();
        assertThat(key.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(key.getPortoneBillingKey()).isEqualTo(KEY);
        assertThat(key.getIssuedAt()).isEqualTo(ISSUED_AT);
        assertThat(key.getDeleteRequestedAt()).isNull();
        assertThat(key.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("ACTIVE 키를 교체하면 REPLACED가 되고 true를 돌려준다")
    void replaceActive() {
        // given
        BillingKey key = BillingKey.issued(MEMBER_ID, KEY, ISSUED_AT);

        // when
        boolean changed = key.replace();

        // then
        assertThat(changed).isTrue();
        assertThat(key.getStatus()).isEqualTo(BillingKeyStatus.REPLACED);
        assertThat(key.isActive()).isFalse();
    }

    @Test
    @DisplayName("이미 REPLACED인 키의 교체는 바꾸지 않고 false다 — 같은 처리가 두 번 지나가도 안전하다")
    void replaceIsIdempotent() {
        // given
        BillingKey key = withStatus(BillingKeyStatus.REPLACED);

        // when
        boolean changed = key.replace();

        // then
        assertThat(changed).isFalse();
        assertThat(key.getStatus()).isEqualTo(BillingKeyStatus.REPLACED);
    }

    @ParameterizedTest
    @EnumSource(value = BillingKeyStatus.class, names = {"REVOKED", "DELETE_REQUESTED", "DELETED"})
    @DisplayName("청구 불가 상태의 키는 교체할 수 없다 — 잠금 밖에서 누가 바꿨다는 뜻이라 덮어쓰지 않는다")
    void replaceRejectsUnusable(BillingKeyStatus status) {
        // given
        BillingKey key = withStatus(status);

        // when
        Throwable thrown = catchThrowable(key::replace);

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(key.getStatus()).isEqualTo(status);
    }

    @Test
    @DisplayName("REPLACED 키를 되살리면 ACTIVE가 되고 true다")
    void reactivateReplaced() {
        // given
        BillingKey key = withStatus(BillingKeyStatus.REPLACED);

        // when
        boolean changed = key.reactivate();

        // then
        assertThat(changed).isTrue();
        assertThat(key.isActive()).isTrue();
    }

    @Test
    @DisplayName("이미 ACTIVE인 키의 되살리기는 바꾸지 않고 false다")
    void reactivateIsIdempotent() {
        // given
        BillingKey key = BillingKey.issued(MEMBER_ID, KEY, ISSUED_AT);

        // when
        boolean changed = key.reactivate();

        // then
        assertThat(changed).isFalse();
        assertThat(key.isActive()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = BillingKeyStatus.class, names = {"REVOKED", "DELETE_REQUESTED", "DELETED"})
    @DisplayName("청구 불가 상태의 키는 되살릴 수 없다 — 서비스가 BILLING-006으로 먼저 거르므로 여기 오면 호출 순서 버그다")
    void reactivateRejectsUnusable(BillingKeyStatus status) {
        // given
        BillingKey key = withStatus(status);

        // when
        Throwable thrown = catchThrowable(key::reactivate);

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(key.getStatus()).isEqualTo(status);
    }

    @Test
    @DisplayName("toString에 빌링키 원문이 없다 — 예외 메시지와 로그에 섞이는 문자열이다")
    void toStringHidesKey() {
        // given
        BillingKey key = BillingKey.issued(MEMBER_ID, KEY, ISSUED_AT);

        // when
        String text = key.toString();

        // then
        assertThat(text).doesNotContain(KEY);
        assertThat(text).contains("ACTIVE");
    }

    private static BillingKey withStatus(BillingKeyStatus status) {
        return BillingKey.builder()
            .memberId(MEMBER_ID)
            .portoneBillingKey(KEY)
            .status(status)
            .issuedAt(ISSUED_AT)
            .build();
    }
}
