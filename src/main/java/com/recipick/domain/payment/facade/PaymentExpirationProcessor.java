package com.recipick.domain.payment.facade;

import com.recipick.domain.order.entity.enums.CancelReasonCode;
import com.recipick.domain.order.facade.OrderFacade;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.service.PaymentExpirationOutcome;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.domain.payment.service.PointCommandService;
import com.recipick.domain.product.dto.StockQuantity;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// PG에 결제가 없거나(404) 결제창만 열린 채 (READY) 5분이 지난 결제를 닫는다. 상태를 바꾸는 곳은 여기뿐
// PortOne 조회는 호출한 쪽(만료 Facade)이 트랜잭션 밖에서 끝낸 뒤 부른다. PG가 PAID·FAILED를 보인 결제는
// 여기로 오지 않고 PaymentConfirmProcessor가 판정한다
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpirationProcessor {

    private static final String EXPIRED_DETAIL = "결제 대기 5분 만료";

    private final PaymentService paymentService;
    private final OrderFacade orderFacade;
    private final PointCommandService pointCommandService;
    private final Clock clock;

    // 락 순서 PAYMENT → ORDERS → MEMBER_POINT → POINT_EARNING → 재고. 확정 API·웹훅과 같은 PAYMENT 잠금을 잡으므로
    // 먼저 잡은 쪽만 상태를 바꾼다
    @Transactional
    public PaymentExpirationOutcome expire(Long paymentId) {
        Payment payment = paymentService.getByIdForUpdate(paymentId);
        // PortOne 조회와 잠금 사이에 확정·웹훅·결제 전 취소가 먼저 끝냈을 수 있다
        if (payment.getStatus() != PaymentStatus.IN_PROGRESS) {
            log.info("이미 끝난 결제라 만료하지 않음: paymentId={}, status={}", paymentId, payment.getStatus());
            return PaymentExpirationOutcome.SKIPPED;
        }
        // 큐가 일찍 꺼냈거나 호출 쪽 판단이 틀렸을 때의 마지막 방어. 현재 시각이 만료 시각과 같으면 만료로 본다
        LocalDateTime now = LocalDateTime.now(clock);
        if (now.isBefore(payment.getExpiresAt())) {
            log.warn("아직 만료 전이라 결제를 닫지 않음: paymentId={}, expiresAt={}", paymentId, payment.getExpiresAt());
            return PaymentExpirationOutcome.SKIPPED;
        }

        // PG 거래가 없거나 확정되지 않은 결제라 기록할 거래 ID가 없다
        payment.fail(PaymentFailureReason.EXPIRED, null);
        // 주문 종료가 pending_member_id를 풀어 회원이 다음 주문을 만들 수 있게 된다. 이미 종료된 주문이면 빈 목록이 와서
        // 재고를 다시 복구하지 않는다
        List<StockQuantity> items = orderFacade.closeByPaymentFailure(
            payment.getOrderId(), CancelReasonCode.PAYMENT_TIMEOUT, EXPIRED_DETAIL);
        // 예약했던 포인트를 원래 적립 건으로 되돌리고 적립 예정을 취소한다. 이미 되돌렸으면 아무것도 바꾸지 않는다
        pointCommandService.restoreUsageAndCancelEarning(payment.getOrderId());
        orderFacade.restoreStock(items);
        log.info("결제 5분 만료 처리: paymentId={}, orderId={}", paymentId, payment.getOrderId());
        return PaymentExpirationOutcome.EXPIRED;
    }
}
