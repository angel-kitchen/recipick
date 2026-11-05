package com.recipick.infra.portone;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;

// 운영은 환경 변수로만 PortOne 설정을 넣는다(deploy/compose.yml). 환경 변수 이름이 레코드 필드에 실제로 닿는지 Spring 컨텍스트 없이
// Binder로 본다. PORTONE_BILLING_CHANNEL_KEY는 설정 이름의 하이픈을 밑줄로 바꾼 호환 규칙에 기대므로 테스트로 못 박는다
class PortOnePropertiesBindingTest {

    @Test
    @DisplayName("PORTONE_BILLING_CHANNEL_KEY는 billingChannelKey에, PORTONE_CHANNEL_KEY는 channelKey에 따로 바인딩된다")
    void bindsBillingChannelKeyFromEnvironmentVariable() {
        // given
        Map<String, Object> environmentVariables = Map.of(
            "PORTONE_API_SECRET", "secret",
            "PORTONE_STORE_ID", "store-1",
            "PORTONE_CHANNEL_KEY", "channel-key-order",
            "PORTONE_BILLING_CHANNEL_KEY", "channel-key-billing");

        // when
        PortOneProperties properties = bind(environmentVariables);

        // then
        assertThat(properties.storeId()).isEqualTo("store-1");
        assertThat(properties.channelKey()).isEqualTo("channel-key-order");
        assertThat(properties.billingChannelKey()).isEqualTo("channel-key-billing");
    }

    @Test
    @DisplayName("PORTONE_BILLING_CHANNEL_KEY가 없으면 billingChannelKey는 비어 있고 일반결제 키로 채워지지 않는다")
    void billingChannelKeyStaysEmptyWithoutEnvironmentVariable() {
        // given
        Map<String, Object> environmentVariables = Map.of(
            "PORTONE_API_SECRET", "secret",
            "PORTONE_CHANNEL_KEY", "channel-key-order");

        // when
        PortOneProperties properties = bind(environmentVariables);

        // then
        assertThat(properties.channelKey()).isEqualTo("channel-key-order");
        assertThat(properties.billingChannelKey()).isNull();
        assertThat(properties.membershipRefundReadTimeout()).isEqualTo(Duration.ofSeconds(60));
    }

    // 이름이 systemEnvironment로 끝나는 SystemEnvironmentPropertySource여야 Spring Boot가 환경 변수 이름 규칙(대문자·밑줄)으로 읽는다
    private static PortOneProperties bind(Map<String, Object> environmentVariables) {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(
            new SystemEnvironmentPropertySource("test-systemEnvironment", environmentVariables));
        return Binder.get(environment).bind("portone", PortOneProperties.class).get();
    }
}
