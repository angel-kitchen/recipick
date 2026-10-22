package com.recipick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.domain.payment.dto.response.PortOneConfigResponse;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.infra.portone.PortOneProperties;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PortOneConfigServiceTest {

    @Test
    @DisplayName("storeId와 channelKey가 있으면 공개 설정 두 값만 반환한다")
    void getPublicConfig() {
        // given
        PortOneConfigService service = new PortOneConfigService(
            properties("test-store-id", "test-channel-key")
        );

        // when
        PortOneConfigResponse response = service.getPublicConfig();

        // then
        assertThat(response.storeId()).isEqualTo("test-store-id");
        assertThat(response.channelKey()).isEqualTo("test-channel-key");
    }

    @ParameterizedTest
    @MethodSource("missingConfigs")
    @DisplayName("storeId 또는 channelKey가 비어 있으면 PAYMENT-010이다")
    void getPublicConfigWithMissingConfig(String storeId, String channelKey) {
        // given
        PortOneConfigService service = new PortOneConfigService(
            properties(storeId, channelKey)
        );

        // when, then
        assertThatThrownBy(service::getPublicConfig)
            .isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(PaymentErrorCode.PORTONE_CONFIG_ERROR)
            );
    }

    static Stream<Arguments> missingConfigs() {
        return Stream.of(
            Arguments.of(null, "test-channel-key"),
            Arguments.of("", "test-channel-key"),
            Arguments.of("test-store-id", null),
            Arguments.of("test-store-id", "   ")
        );
    }

    private PortOneProperties properties(String storeId, String channelKey) {
        return new PortOneProperties(
            "test-secret",
            "https://api.portone.io",
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            storeId,
            channelKey
        );
    }
}
