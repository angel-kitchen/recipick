package com.recipick.domain.payment.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 멤버십 자동결제 설정(recipick.membership-billing). enabled=false면 새 청구(바로 구독 신규 접수·체험 전환 접수·READY 재접수·PG POST)를
// 막는다. 이미 생긴 결제의 재조회·결과 반영·복구·환불 요청 저장은 이 값과 무관하게 돈다(K-2). 운영 기본값은 false(application.yml).
// 시간 값은 이용권 생명주기 초기값(L 10분·M 2분·W 24시간·D 24시간). 첫 결제 재접수 한도는 접수 유효 창 30분·회차당 시도 3개.
// 갱신은 확정 실패 뒤 24시간(R)마다, 회차당 PG 발송 3회(N)까지 — 첫 결제의 시도 행 3개와 다른 기준이다
@ConfigurationProperties("recipick.membership-billing")
public record MembershipBillingProperties(
    boolean enabled,
    @DefaultValue("10m") Duration lease,
    @DefaultValue("2m") Duration dispatchMargin,
    @DefaultValue("24h") Duration unknownWindow,
    @DefaultValue("24h") Duration longDelay,
    @DefaultValue("30m") Duration firstChargeAcceptWindow,
    @DefaultValue("3") int firstChargeMaxAttempts,
    @DefaultValue("100") int batchSize,
    @DefaultValue("24h") Duration retryInterval,
    @DefaultValue("3") int renewalMaxDispatches
) {

    // 갱신 재시도 간격(R)과 회차당 PG 발송 한도(N)는 동결된 정책값이다. 설정으로 다른 값이 들어오면 조용히 따르지 않고 기동을 멈춘다
    public static final Duration POLICY_RETRY_INTERVAL = Duration.ofHours(24);
    public static final int POLICY_RENEWAL_MAX_DISPATCHES = 3;

    public MembershipBillingProperties {
        if (!POLICY_RETRY_INTERVAL.equals(retryInterval) || renewalMaxDispatches != POLICY_RENEWAL_MAX_DISPATCHES) {
            throw new IllegalStateException("멤버십 갱신 재시도 간격·발송 한도가 정책값(24시간·3회)과 다릅니다");
        }
    }
}
