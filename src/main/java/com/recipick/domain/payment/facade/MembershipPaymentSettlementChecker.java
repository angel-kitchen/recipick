package com.recipick.domain.payment.facade;

import com.recipick.domain.member.service.MembershipBillingService;
import com.recipick.domain.member.service.WithdrawalSettlementChecker;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.service.MembershipPaymentQueryService;
import com.recipick.global.exception.BusinessException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 탈퇴 정산의 MEMBERSHIP_PAYMENT 영역. 회원 탈퇴 트랜잭션(회원 잠금 뒤)이 Scope 순서대로 부르며 판정만 한다.
// 이 회원의 모든 이용권(종료된 것 포함)에 대해 C-17 다섯 조건(미해결 회차·미해결 시도·환불 REQUESTED·환불 미완료 PAID·강한 차단)을
// 보고 하나라도 있으면 409로 거절한다. 조회 예외는 잡지 않는다 — 탈퇴 트랜잭션과 함께 롤백된다.
// 자동결제 영역(AUTO_BILLING)은 이 Checker가 맡지 않는다 — 그 영역이 연결되기 전까지 탈퇴는 계속 열리지 않는다
@Component
@Order(200)
@RequiredArgsConstructor
public class MembershipPaymentSettlementChecker implements WithdrawalSettlementChecker {

    private final MembershipBillingService membershipBillingService;
    private final MembershipPaymentQueryService membershipPaymentQueryService;

    @Override
    public Set<Scope> supportedScopes() {
        return Set.of(Scope.MEMBERSHIP_PAYMENT);
    }

    // 확인 잔액은 포인트 영역만 쓴다
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireSettled(Long memberId, Long acknowledgedPointBalance) {
        if (membershipPaymentQueryService.existsUnresolved(membershipBillingService.findMembershipIds(memberId))) {
            throw new BusinessException(PaymentErrorCode.MEMBERSHIP_PAYMENT_UNSETTLED_FOR_WITHDRAWAL);
        }
    }
}
