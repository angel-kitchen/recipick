package com.recipick.domain.payment.service;

import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class PaymentVerifier {

    // 1차 문서 4절의 검증 번호 순서(0 → 4 → 5 → 6 → 7)로 본다. 프론트가 보낸 값은 쓰지 않고,
    // 상태를 바꾸거나 예외를 던지지 않고 판정만 돌려준다. 집행은 Processor 몫이다
    public PaymentApprovalOutcome verify(Payment payment, PortOnePayment portOnePayment) {
        PaymentStatus status = payment.getStatus();
        if (status == PaymentStatus.PAID) {
            return PaymentApprovalOutcome.ALREADY_PAID;
        }
        if (status == PaymentStatus.FAILED || status == PaymentStatus.CANCELED) {
            return PaymentApprovalOutcome.ALREADY_CLOSED;
        }
        if (status != PaymentStatus.IN_PROGRESS) {
            return PaymentApprovalOutcome.INVALID_STATUS;
        }

        PortOnePaymentStatus portOneStatus = portOnePayment.status();
        if (portOneStatus == PortOnePaymentStatus.FAILED) {
            return PaymentApprovalOutcome.PG_FAILED;
        }
        if (portOneStatus == PortOnePaymentStatus.CANCELED
            || portOneStatus == PortOnePaymentStatus.PARTIAL_CANCELED) {
            // PG 쪽에서 취소된 결제. 즉시 닫을지는 테스트 채널 확인(메모 검증 ②·⑤) 뒤 정하므로 상태를 바꾸지 않는다
            return PaymentApprovalOutcome.INVALID_STATUS;
        }
        if (portOneStatus != PortOnePaymentStatus.PAID) {
            return PaymentApprovalOutcome.NOT_PAID;
        }

        // customData가 없거나 해석 불가면 null이 오고, 그것도 불일치다
        if (!Objects.equals(portOnePayment.customDataOrderId(), payment.getOrderId())) {
            return PaymentApprovalOutcome.ORDER_MISMATCH;
        }

        Long totalAmount = portOnePayment.totalAmount();
        if (totalAmount == null) {
            // PAID인데 금액이 없으면 승인 근거가 없는 것이지 불일치가 아니다. AMOUNT_MISMATCH는 환불·주문 종료를 집행하므로 쓰지 않는다
            return PaymentApprovalOutcome.NOT_PAID;
        }
        // 주문 금액이 아니라 결제 생성 때 찍어 둔 PAYMENT.amount 스냅샷과 비교한다
        if (!totalAmount.equals(payment.getAmount())) {
            return PaymentApprovalOutcome.AMOUNT_MISMATCH;
        }

        LocalDateTime paidAt = portOnePayment.paidAt();
        if (paidAt == null) {
            // PAID인데 완료 시각이 없으면 승인 근거가 없는 것이지 만료가 아니다. EXPIRED는 환불·주문 종료를 집행하므로 쓰지 않는다
            return PaymentApprovalOutcome.NOT_PAID;
        }
        // 서버 시각이 아니라 PG가 확인한 결제 완료 시각으로 판정한다 (같으면 통과)
        if (paidAt.isAfter(payment.getExpiresAt())) {
            return PaymentApprovalOutcome.EXPIRED;
        }
        return PaymentApprovalOutcome.APPROVED;
    }
}
