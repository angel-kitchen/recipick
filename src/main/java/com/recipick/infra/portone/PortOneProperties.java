package com.recipick.infra.portone;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;


// PortOne V2 API 연동 설정. API Secret은 application-local.yml에서만 읽고 로그·응답에 남기지 않는다.
// membershipRefundReadTimeout은 멤버십 환불(금액 지정 취소) 전용 RestClient의 읽기 한도 — PortOne 운영 권장 최소 60초.
// 기존 빈의 readTimeout(5초)은 조회·전액 취소에 그대로 쓴다.
// channelKey는 주문 일반결제 채널, billingChannelKey는 카드 등록(빌링키 발급·검증) 전용 정기결제 채널이다.
// KG이니시스는 두 용도의 MID가 달라 한 채널 키로 둘 다 할 수 없다. billingChannelKey가 비어 있어도 앱은 뜨고,
// 카드 등록만 설정 누락으로 거절된다 — 일반결제 채널 키로 대신하지 않는다

@Validated
@ConfigurationProperties("portone")
public record PortOneProperties(
    @NotBlank String apiSecret,
    @DefaultValue("https://api.portone.io") String baseUrl,
    @DefaultValue("3s") Duration connectTimeout,
    @DefaultValue("5s") Duration readTimeout,
    String storeId,
    String channelKey,
    @DefaultValue("60s") Duration membershipRefundReadTimeout,
    String billingChannelKey
) {

    @ConstructorBinding
    public PortOneProperties {
    }

    // 카드 등록 채널 키를 따로 주지 않는 기존 호출부용(멤버십 환불 읽기 한도만 지정)
    public PortOneProperties(String apiSecret, String baseUrl, Duration connectTimeout, Duration readTimeout,
                             String storeId, String channelKey, Duration membershipRefundReadTimeout) {
        this(apiSecret, baseUrl, connectTimeout, readTimeout, storeId, channelKey, membershipRefundReadTimeout, null);
    }

    // 멤버십 환불 읽기 한도를 따로 주지 않는 기존 호출부용(기본값 60초)
    public PortOneProperties(String apiSecret, String baseUrl, Duration connectTimeout, Duration readTimeout,
                             String storeId, String channelKey) {
        this(apiSecret, baseUrl, connectTimeout, readTimeout, storeId, channelKey, Duration.ofSeconds(60));
    }
}
