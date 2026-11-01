package com.recipick.domain.payment.service;

// C-11 결과. 빌링키·고객 정보는 담지 않는다
public record MembershipSubscribeResult(Long membershipId, MembershipSubscribeStatus status) {
}
