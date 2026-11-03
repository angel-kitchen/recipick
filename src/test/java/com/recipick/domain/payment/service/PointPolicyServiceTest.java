package com.recipick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.domain.member.entity.enums.MembershipType;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PointPolicyServiceTest {

    private final PointPolicyService pointPolicyService = new PointPolicyService();

    @Test
    @DisplayName("이용권이 없는 일반 회원은 0.5%이고 주문 적립률 컬럼과 같은 소수 넷째 자리 값이다")
    void standardRateWithoutMembership() {
        // when
        BigDecimal rate = pointPolicyService.resolveRate(Optional.empty());

        // then
        assertThat(rate).isEqualTo(new BigDecimal("0.0050"));
        assertThat(rate.scale()).isEqualTo(4);
    }

    @Test
    @DisplayName("무료체험 회원은 일반 회원과 같은 0.5%다")
    void standardRateForTrial() {
        // when
        BigDecimal rate = pointPolicyService.resolveRate(Optional.of(MembershipType.TRIAL));

        // then
        assertThat(rate).isEqualTo(new BigDecimal("0.0050"));
    }

    @Test
    @DisplayName("유료 회원은 1%이고 주문 적립률 컬럼과 같은 소수 넷째 자리 값이다")
    void paidRate() {
        // when
        BigDecimal rate = pointPolicyService.resolveRate(Optional.of(MembershipType.PAID));

        // then
        assertThat(rate).isEqualTo(new BigDecimal("0.0100"));
        assertThat(rate.scale()).isEqualTo(4);
    }

    @Test
    @DisplayName("이용권 조회 결과 없이 적립률을 묻는 것은 호출자 버그라 막는다")
    void rejectNullMembershipType() {
        // when, then
        assertThatThrownBy(() -> pointPolicyService.resolveRate(null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
