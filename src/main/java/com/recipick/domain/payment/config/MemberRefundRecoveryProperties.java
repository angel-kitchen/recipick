package com.recipick.domain.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 회원 환불 재확인이 PortOne에 이 환불의 취소가 없음을 확인했을 때 같은 서버 멱등 키로 취소 POST를 다시 보내는 스위치.
// 꺼져 있으면 재확인은 기존대로 조회만 하고 다시 예약한다. 운영 기본 false — 실 DB 검증을 끝낸 뒤 명시적으로 켠다
@ConfigurationProperties("payment.member-refund-recovery")
public record MemberRefundRecoveryProperties(
    @DefaultValue("false") boolean autoRepostEnabled
) {
}
