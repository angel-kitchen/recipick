package com.recipick.domain.payment.repository.dto;

import com.recipick.domain.payment.entity.enums.PaymentStatus;
import java.time.LocalDateTime;
import java.util.Collection;

// 관리자 결제 검색 조건. 결제 Repository는 회원을 모르므로 memberId 대신 주문 도메인이 준 orderIds를 받는다.
// 값이 null인 조건은 걸지 않는다. orderIds가 빈 목록이면 결과가 없으므로 호출자(Facade)가 조회 전에 끊는다
public record PaymentAdminSearchCondition(
    PaymentStatus status,
    Collection<Long> orderIds,
    LocalDateTime fromAt,
    LocalDateTime toExclusive
) {
}
