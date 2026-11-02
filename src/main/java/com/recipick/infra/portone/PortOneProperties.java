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

@Validated
@ConfigurationProperties("portone")
public record PortOneProperties(
    @NotBlank String apiSecret,
    @DefaultValue("https://api.portone.io") String baseUrl,
    @DefaultValue("3s") Duration connectTimeout,
    @DefaultValue("5s") Duration readTimeout,
    String storeId,
    String channelKey,
    @DefaultValue("60s") Duration membershipRefundReadTimeout
) {

    @ConstructorBinding
    public PortOneProperties {
    }

    // 멤버십 환불 읽기 한도를 따로 주지 않는 기존 호출부용(기본값 60초)
    public PortOneProperties(String apiSecret, String baseUrl, Duration connectTimeout, Duration readTimeout,
                             String storeId, String channelKey) {
        this(apiSecret, baseUrl, connectTimeout, readTimeout, storeId, channelKey, Duration.ofSeconds(60));
    }
}
