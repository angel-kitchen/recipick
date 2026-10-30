package com.recipick.domain.payment.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

// 주문에 적용할 적립률을 정한다. 주문은 생성 시점에 이 값을 저장하므로 정책이 바뀌어도 이미 만든 주문은 저장된 값으로 적립한다.
// 2차는 일반·체험 회원 모두 0.5%라 회원을 구분하지 않는다. 회원별로 적립률이 달라지면 이 안에서만 바꾼다
@Service
public class PointPolicyService {
    private static final BigDecimal STANDARD_RATE = new BigDecimal("0.0050");

    public BigDecimal resolveRate(Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("회원 ID는 필수입니다.");
        }
        return STANDARD_RATE;
    }
}
