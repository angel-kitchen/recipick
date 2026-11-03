package com.recipick.domain.payment.service;

import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PaymentVerifier {

    // 상품 결제가 받는 유일한 통화. 정확한 문자열만 인정하고 다른 통화를 원화로 바꾸지 않는다
    private static final String CURRENCY_KRW = "KRW";

    // 1차 문서 4절의 검증 번호 순서(0 → 4 → 5 → 6 → 7)로 본다. 프론트가 보낸 값은 쓰지 않고,
    // 상태를 바꾸거나 예외를 던지지 않고 판정만 돌려준다. 집행은 Processor 몫이다
    public PaymentApprovalOutcome verify(Payment payment, PortOnePayment portOnePayment) {
        PaymentStatus status = payment.getStatus();
        if (status == PaymentStatus.PAID) {
            return PaymentApprovalOutcome.ALREADY_PAID;
        }
        if (status == PaymentStatus.FAILED && portOnePayment.status() == PortOnePaymentStatus.PAID) {
            // 종료된 결제의 늦은 승인. Processor가 ALREADY_CLOSED에서 PG 금액으로 SYSTEM 환불을 접수하므로, 그 전에
            // 이 주문의 거래인지·원화인지·실제 수납액이 있는지 본다 — 검증되지 않은 거래를 자동 환불 대상으로 확정하지 않는다.
            // PAYMENT.amount와 다른 수납액은 막지 않는다. 이 경로는 실제 수납액을 그대로 돌려주는 자리다
            if (!Objects.equals(portOnePayment.customDataOrderId(), payment.getOrderId())) {
                return PaymentApprovalOutcome.ORDER_MISMATCH;
            }
            Optional<PaymentApprovalOutcome> rejected = rejectUnlessKrw(portOnePayment);
            if (rejected.isPresent()) {
                return rejected.get();
            }
            if (portOnePayment.totalAmount() == null) {
                // 수납액을 모르면 환불을 만들 수 없다. 재조회로 채워질 수 있으므로 종료 확정(ALREADY_CLOSED)이 아니라 NOT_PAID
                return PaymentApprovalOutcome.NOT_PAID;
            }
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

        // 원화가 아닌 금액은 주문 금액과 비교할 수 없고 환불 금액의 근거도 되지 않는다. 금액 검사보다 앞에서 끝낸다
        Optional<PaymentApprovalOutcome> rejected = rejectUnlessKrw(portOnePayment);
        if (rejected.isPresent()) {
            return rejected.get();
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

    // 통화가 KRW로 확인되면 비어 있다. 누락은 승인 근거가 없는 것이라 재조회 대상(NOT_PAID)이고,
    // 명확한 다른 통화는 자동으로 승인·환불할 수 없어 운영 확인 대상(CURRENCY_MISMATCH)이다
    private Optional<PaymentApprovalOutcome> rejectUnlessKrw(PortOnePayment portOnePayment) {
        String currency = portOnePayment.currency();
        if (currency == null || currency.isBlank()) {
            return Optional.of(PaymentApprovalOutcome.NOT_PAID);
        }
        if (!CURRENCY_KRW.equals(currency)) {
            return Optional.of(PaymentApprovalOutcome.CURRENCY_MISMATCH);
        }
        return Optional.empty();
    }
}
