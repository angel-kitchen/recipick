package com.recipick.domain.payment.facade;

import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.payment.service.BillingKeyRegistration;
import com.recipick.domain.payment.service.BillingKeyService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 빌링키 등록의 DB 트랜잭션. PG 검증은 Facade가 트랜잭션 밖에서 끝냈다.
// 트랜잭션의 첫 DB 작업은 회원 잠금이다(탈퇴 회원은 MEMBER-001). 그 뒤의 일반 조회는 잠금 뒤에 스냅숏이 만들어져 최신이고,
// 같은 회원의 등록·교체는 이 잠금으로 직렬화된다
@Component
@RequiredArgsConstructor
public class BillingKeyRegistrationProcessor {

    private final MemberService memberService;
    private final BillingKeyService billingKeyService;

    @Transactional
    public BillingKeyRegistration register(Long memberId, Long issueRequestId, String portoneBillingKey,
                                           LocalDateTime issuedAt) {
        memberService.lockActiveMember(memberId);
        return billingKeyService.register(memberId, issueRequestId, portoneBillingKey, issuedAt);
    }
}
