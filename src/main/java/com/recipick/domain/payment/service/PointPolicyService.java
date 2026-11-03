package com.recipick.domain.payment.service;

import com.recipick.domain.member.entity.enums.MembershipType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

// 주문에 적용할 적립률을 정한다. 주문은 생성 시점에 이 값을 저장하므로 정책이 바뀌어도 이미 만든 주문은 저장된 값으로 적립한다.
// 이용권 조회는 주문 조립이 기준시각 하나로 한 번만 하고, 여기서는 그 결과로 적립률만 고른다 — 배송비 판정과 같은 조회 결과를 쓰기 위해서다
@Service
public class PointPolicyService {
    private static final BigDecimal STANDARD_RATE = new BigDecimal("0.0050");
    private static final BigDecimal PAID_RATE = new BigDecimal("0.0100");

    // 유료 이용권은 발급 경로(구독·관리자 지급)와 관계없이 유료 적립률이고, 체험 회원은 일반 회원과 같다
    public BigDecimal resolveRate(Optional<MembershipType> membershipType) {
        if (membershipType == null) {
            throw new IllegalArgumentException("이용권 유형 조회 결과는 필수입니다.");
        }
        return membershipType.filter(type -> type == MembershipType.PAID)
            .map(type -> PAID_RATE)
            .orElse(STANDARD_RATE);
    }
}
