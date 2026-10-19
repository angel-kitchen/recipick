package com.recipick.infra.portone;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PortOneCustomDataParserTest {

    @Test
    @DisplayName("(잠정) customData의 orderId 정수를 읽는다")
    void parseOrderId() {
        // given
        String customData = "{\"orderId\":10}";

        // when
        Long orderId = PortOneCustomDataParser.orderIdOf(customData);

        // then
        assertThat(orderId).isEqualTo(10L);
    }

    @Test
    @DisplayName("(잠정) 다른 필드가 함께 있어도 orderId만 읽는다")
    void parseOrderIdWithExtraFields() {
        // given
        String customData = "{\"orderId\":10,\"extra\":\"x\",\"nested\":{\"a\":1}}";

        // when
        Long orderId = PortOneCustomDataParser.orderIdOf(customData);

        // then
        assertThat(orderId).isEqualTo(10L);
    }

    @Test
    @DisplayName("(잠정) Long 최대값까지 읽는다")
    void parseLongMax() {
        // given
        String customData = "{\"orderId\":9223372036854775807}";

        // when
        Long orderId = PortOneCustomDataParser.orderIdOf(customData);

        // then
        assertThat(orderId).isEqualTo(Long.MAX_VALUE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("(잠정) customData가 없거나 공백이면 null")
    void missingCustomData(String customData) {
        // when
        Long orderId = PortOneCustomDataParser.orderIdOf(customData);

        // then
        assertThat(orderId).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{orderId", "abc", "{\"orderId\":}", "orderId=10"})
    @DisplayName("(잠정) JSON이 아니면 예외 없이 null")
    void malformedJson(String customData) {
        // when
        Long orderId = PortOneCustomDataParser.orderIdOf(customData);

        // then
        assertThat(orderId).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"orderid\":10}", "{\"order_id\":10}", "{\"orderId\":null}"})
    @DisplayName("(잠정) orderId 필드가 없으면 null")
    void missingOrderIdField(String customData) {
        // when
        Long orderId = PortOneCustomDataParser.orderIdOf(customData);

        // then
        assertThat(orderId).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"orderId\":\"10\"}",
            "{\"orderId\":10.5}",
            "{\"orderId\":true}",
            "{\"orderId\":{\"v\":1}}",
            "{\"orderId\":[10]}",
            "{\"orderId\":9223372036854775808}",
            "{\"orderId\":92233720368547758070}"
    })
    @DisplayName("(잠정) orderId가 Long 범위의 정수가 아니면 강제 변환하지 않고 null")
    void orderIdTypeMismatch(String customData) {
        // when
        Long orderId = PortOneCustomDataParser.orderIdOf(customData);

        // then
        assertThat(orderId).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"[10]", "10", "\"orderId\""})
    @DisplayName("(잠정) 루트가 객체가 아니면 null")
    void rootIsNotObject(String customData) {
        // when
        Long orderId = PortOneCustomDataParser.orderIdOf(customData);

        // then
        assertThat(orderId).isNull();
    }
}
