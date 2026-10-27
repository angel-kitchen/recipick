package com.recipick.domain.member.service;

// 회원 잠금 이후 같은 트랜잭션에서 호출한다. 미해결 결제·환불이 있거나 확인할 수 없으면 거절한다.
public interface WithdrawalSettlementChecker {
    void requireSettled(Long memberId);
}
