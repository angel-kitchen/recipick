package com.recipick.domain.member.facade;

import com.recipick.domain.payment.dto.response.MembershipRefundResult;
import com.recipick.domain.payment.facade.MembershipRefundFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MembershipCancelFacade {
    private final MembershipRefundFacade refunds;

    // 접수 커밋·PG 호출·결과 반영은 결제 계약이 담당하며 회원 쪽은 별도로 종료하지 않는다.
    @Transactional(propagation = Propagation.NEVER)
    public MembershipRefundResult terminate(Long memberId, Long membershipId, String idempotencyKey) {
        return refunds.requestMemberRefund(memberId, membershipId, idempotencyKey);
    }
}
