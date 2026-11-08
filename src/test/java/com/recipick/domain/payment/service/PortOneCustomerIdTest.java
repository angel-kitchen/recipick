package com.recipick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PortOneCustomerIdTest {

    // 토스페이먼츠 customerKey 규칙(실제 오류 메시지 기준): 영문 대소문자·숫자·특수문자(-*=.@), 2자 이상 255자 이하
    private static final String TOSS_CUSTOMER_KEY_PATTERN = "^[A-Za-z0-9\\-*=.@]{2,255}$";

    @Test
    @DisplayName("회원 ID 한 자리도 토스페이먼츠 customerKey 규칙(허용 문자, 2~255자)을 만족한다")
    void satisfiesTossCustomerKeyRule() {
        // given
        Long singleDigitMemberId = 6L;
        Long largeMemberId = Long.MAX_VALUE;

        // when
        String small = PortOneCustomerId.of(singleDigitMemberId);
        String large = PortOneCustomerId.of(largeMemberId);

        // then
        assertThat(small).isEqualTo("member-6").matches(TOSS_CUSTOMER_KEY_PATTERN);
        assertThat(large).matches(TOSS_CUSTOMER_KEY_PATTERN);
    }

    @Test
    @DisplayName("같은 회원은 항상 같은 고객 ID가 되고 다른 회원과 겹치지 않는다")
    void isStableAndDistinctPerMember() {
        // given
        Long memberId = 7L;

        // when
        String first = PortOneCustomerId.of(memberId);
        String second = PortOneCustomerId.of(memberId);
        String other = PortOneCustomerId.of(8L);

        // then
        assertThat(first).isEqualTo(second).isNotEqualTo(other);
    }
}
