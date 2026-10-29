package com.recipick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PointPolicyServiceTest {

    private final PointPolicyService pointPolicyService = new PointPolicyService();

    @Test
    @DisplayName("2차 적립률은 모든 회원 0.5%이고 주문 적립률 컬럼과 같은 소수 넷째 자리 값이다")
    void resolveRate() {
        // when
        BigDecimal rate = pointPolicyService.resolveRate(1L);

        // then
        assertThat(rate).isEqualTo(new BigDecimal("0.0050"));
    }

    @Test
    @DisplayName("회원 ID 없이 적립률을 묻는 것은 호출자 버그라 막는다")
    void rejectNullMemberId() {
        // when, then
        assertThatThrownBy(() -> pointPolicyService.resolveRate(null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
