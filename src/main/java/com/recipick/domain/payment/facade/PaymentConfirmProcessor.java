package com.recipick.domain.payment.facade;

import com.recipick.domain.order.entity.enums.CancelReasonCode;
import com.recipick.domain.order.facade.OrderFacade;
import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.service.PaymentApprovalOutcome;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.domain.payment.service.PaymentVerifier;
import com.recipick.domain.product.dto.StockQuantity;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentConfirmProcessor {

    private static final String EXPIRED_DETAIL = "결제 대기 5분 만료";
    private static final String LATE_APPROVAL_DETAIL = "종료된 결제의 늦은 승인";

    private final PaymentService paymentService;
    private final PaymentVerifier paymentVerifier;
    private final OrderService orderService;
    private final OrderFacade orderFacade;

    @Transactional
    public PaymentApprovalOutcome process(Long orderId, PortOnePayment portOnePayment) {
        Payment payment = paymentService.getByOrderIdForUpdate(orderId);
        PaymentApprovalOutcome outcome = paymentVerifier.verify(payment, portOnePayment);

        switch (outcome) {
            case APPROVED -> {
                payment.markPaid(portOnePayment.transactionId(), portOnePayment.paidAt());
                orderService.markPaid(orderId, portOnePayment.paidAt());
            }
            case PG_FAILED -> {
                payment.fail(PaymentFailureReason.PG_FAILED, portOnePayment.transactionId());
                closeOrder(orderId, CancelReasonCode.PAYMENT_FAILED, portOnePayment.failureSummary());
            }
            case AMOUNT_MISMATCH -> {
                String detail = String.format("결제 금액 불일치 — 주문 %d원, PG 결제 %d원",
                    payment.getAmount(), portOnePayment.totalAmount());
                payment.fail(PaymentFailureReason.AMOUNT_MISMATCH, portOnePayment.transactionId());
                requestSystemRefund(payment, portOnePayment.totalAmount(), detail);
                closeOrder(orderId, CancelReasonCode.AMOUNT_MISMATCH, detail);
            }
            case EXPIRED -> {
                payment.fail(PaymentFailureReason.EXPIRED, portOnePayment.transactionId());
                requestSystemRefund(payment, portOnePayment.totalAmount(), EXPIRED_DETAIL);
                closeOrder(orderId, CancelReasonCode.PAYMENT_TIMEOUT, EXPIRED_DETAIL);
            }
            case ALREADY_CLOSED -> {
                // 만료 큐가 먼저 FAILED로 닫았는데 PG 승인이 뒤늦게 확인된 경우. 결제는 FAILED 유지, 주문·재고는 이미 닫혀 있어
                // 건드리지 않고 돈만 돌려준다 (주문-결제 정책 5절 "만료 뒤 늦은 승인"). CANCELED는 취소 흐름 몫이라 제외
                if (payment.getStatus() == PaymentStatus.FAILED
                    && portOnePayment.status() == PortOnePaymentStatus.PAID) {
                    Long pgAmount = portOnePayment.totalAmount();
                    if (pgAmount == null) {
                        // 0번에서 끝난 경로라 6번 금액 검사를 안 거쳤다. 실제 청구액을 모르면 PAYMENT.amount로 대신 적지 않고
                        // 기록을 보류한다 — 재전송·재조회로 다시 들어올 때 금액이 오면 그때 만든다
                        log.warn("종료된 결제의 늦은 승인인데 PG 결제 금액이 없음: paymentId={}, orderId={}",
                            payment.getId(), payment.getOrderId());
                    } else {
                        requestSystemRefund(payment, pgAmount, LATE_APPROVAL_DETAIL);
                    }
                }
            }
            // ALREADY_PAID·INVALID_STATUS·NOT_PAID·ORDER_MISMATCH: 아무것도 바꾸지 않는다
            default -> { }
        }
        return outcome;
    }

    // 돈이 PG에 남은 채 결제가 실패로 끝나는 자리다. 행으로 남기고 운영이 볼 수 있게 WARN을 남긴다.
    // 재진입이면 기존 행이 돌아오므로 "생성"이 아니라 "필요 상태"로 적는다.
    // 실제 취소는 이 트랜잭션 커밋 뒤 호출한 쪽(확정 API·웹훅)이 SystemRefundExecutionFacade.scheduleAfterConfirm으로 예약한다
    private void requestSystemRefund(Payment payment, Long amount, String reason) {
        paymentService.requestSystemRefund(payment, amount, reason);
        log.warn("SYSTEM 환불 필요 상태: paymentId={}, orderId={}, amount={}, reason={}",
            payment.getId(), payment.getOrderId(), amount, reason);
    }

    // 락 순서 PAYMENT → ORDERS → (MEMBER_POINT → POINT_EARNING) → 재고. 주문 종료와 재고 복구 사이가 포인트 자리다
    private void closeOrder(Long orderId, CancelReasonCode reasonCode, String reasonDetail) {
        List<StockQuantity> items = orderFacade.closeByPaymentFailure(orderId, reasonCode, reasonDetail);
        orderFacade.restoreStock(items);
    }
}
