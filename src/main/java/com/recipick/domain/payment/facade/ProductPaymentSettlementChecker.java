package com.recipick.domain.payment.facade;

import com.recipick.domain.member.service.WithdrawalSettlementChecker;
import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.global.exception.BusinessException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 탈퇴 정산의 PRODUCT_PAYMENT 영역. 회원 탈퇴 트랜잭션(회원 잠금 뒤)이 Scope 순서대로 부르며, 결제는 변경 없이 판정만 하므로 맨 앞(100)이다.
// 회원의 주문 ID는 주문 Service에서, 미해결 판정은 결제 Service에서 받아 여기서 잇는다(도메인 간 호출은 Facade에서 조립).
// 미해결이 있으면 409로 탈퇴를 거절한다. 탈퇴 검사 뒤에 도착한 거래의 후속 처리는 막지 않는다 — 결제·환불 처리는 탈퇴 여부를 보지 않는다
@Component
@Order(100)
@RequiredArgsConstructor
public class ProductPaymentSettlementChecker implements WithdrawalSettlementChecker {

    private final OrderService orderService;
    private final PaymentService paymentService;

    @Override
    public Set<Scope> supportedScopes() {
        return Set.of(Scope.PRODUCT_PAYMENT);
    }

    // 확인 잔액은 포인트 영역만 쓴다
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireSettled(Long memberId, Long acknowledgedPointBalance) {
        if (paymentService.existsUnsettledForWithdrawal(orderService.findOrderIdsOf(memberId))) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_UNSETTLED_FOR_WITHDRAWAL);
        }
    }
}
