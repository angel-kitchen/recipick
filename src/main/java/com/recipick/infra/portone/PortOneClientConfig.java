package com.recipick.infra.portone;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

// PortOne 전용 RestClient 설정.

@Configuration
@EnableConfigurationProperties({PortOneProperties.class, PortOneWebhookProperties.class})
public class PortOneClientConfig {

    public static final String REST_CLIENT_BEAN = "portOneRestClient";
    public static final String MEMBERSHIP_REFUND_REST_CLIENT_BEAN = "portOneMembershipRefundRestClient";

    private static final String AUTHORIZATION_SCHEME = "PortOne ";

    // PortOne 응답만 관대하게 읽는 매퍼. 알 수 없는 필드는 무시하고, 알 수 없는 enum 값은 {@code @JsonEnumDefaultValue}로 받아 PortOne이 상태 값을 추가해도 조회가 깨지지 않게 한다
    public static ObjectMapper newPortOneObjectMapper() {
        return JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    }

    @Bean(REST_CLIENT_BEAN)
    public RestClient portOneRestClient(PortOneProperties properties) {
        return configure(RestClient.builder(), properties).build();
    }

    // 멤버십 환불(금액 지정 취소) 전용. 취소 POST는 PG 처리 시간이 길 수 있어 읽기 한도만 다르고 나머지 설정은 같다
    @Bean(MEMBERSHIP_REFUND_REST_CLIENT_BEAN)
    public RestClient portOneMembershipRefundRestClient(PortOneProperties properties) {
        return configureForMembershipRefund(RestClient.builder(), properties).build();
    }

    // 테스트가 MockRestServiceServer.bindTo(builder)로 같은 설정을 검증할 수 있게 빌더 단계로 분리한다
    static RestClient.Builder configure(RestClient.Builder builder, PortOneProperties properties) {
        return configure(builder, properties, properties.readTimeout());
    }

    static RestClient.Builder configureForMembershipRefund(RestClient.Builder builder, PortOneProperties properties) {
        return configure(builder, properties, properties.membershipRefundReadTimeout());
    }

    private static RestClient.Builder configure(RestClient.Builder builder, PortOneProperties properties,
                                                Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout())
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        return builder
            .baseUrl(properties.baseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, AUTHORIZATION_SCHEME + properties.apiSecret())
            .requestFactory(requestFactory)
            .messageConverters(converters -> {
                converters.clear();
                converters.add(new MappingJackson2HttpMessageConverter(newPortOneObjectMapper()));
            });
    }
}
