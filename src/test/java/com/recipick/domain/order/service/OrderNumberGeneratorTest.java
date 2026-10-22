package com.recipick.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderNumberGeneratorTest {

    @Test
    @DisplayName("주문번호는 한국 날짜 8자리와 숫자·대문자 10자리로 만든다")
    void generate() {
        // given
        Clock clock = Clock.fixed(Instant.parse("2026-10-21T15:30:00Z"), ZoneId.of("Asia/Seoul"));
        OrderNumberGenerator generator = new OrderNumberGenerator(clock);

        // when
        String orderNumber = generator.generate();

        // then
        assertThat(orderNumber).matches("20261022-[0-9A-Z]{10}");
    }
}
