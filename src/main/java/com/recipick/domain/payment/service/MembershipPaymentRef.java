package com.recipick.domain.payment.service;

import com.recipick.domain.payment.entity.enums.MembershipPaymentStatus;
import java.time.LocalDateTime;

// 회차의 ID·값 투영(잠금 전 일반 조회용). 갱신 스케줄러가 대상 회차를 고를 때만 쓰고, 판정·변경은 회원부터 다시 잠근 뒤
// 잠금 조회로 읽은 엔티티로 한다
public record MembershipPaymentRef(
    Long paymentId,
    Long membershipId,
    MembershipPaymentStatus status,
    LocalDateTime graceDeadlineAt,
    LocalDateTime nextRetryAt
) {
}
