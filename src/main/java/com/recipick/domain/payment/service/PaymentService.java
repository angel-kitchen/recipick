package com.recipick.domain.payment.service;

import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.repository.PaymentRepository;
import com.recipick.domain.payment.repository.RefundRepository;
import com.recipick.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final Clock clock;

    public Payment getByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    // 상태를 바꾸는 흐름의 첫 줄에서 부른다. 호출자 트랜잭션에 참여해야 잠금이 유지된다
    @Transactional
    public Payment getByOrderIdForUpdate(Long orderId) {
        return paymentRepository.findByOrderIdForUpdate(orderId)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    // SYSTEM 환불은 한 결제에 최대 한 건이다. 웹훅 재전송·재조회로 같은 자리에 다시 들어와도
    // 기존 행(REQUESTED·COMPLETED·FAILED 무관)을 돌려주고 새 키를 만들지 않는다 (1차 문서 8절).
    // amount는 PG가 실제로 청구한 금액이다 — 금액 불일치면 PAYMENT.amount와 다르다. 호출 전에 PAYMENT 행이 잠겨 있어야 한다
    @Transactional
    public Refund requestSystemRefund(Payment payment, Long amount, String reason) {
        return refundRepository.findSystemRefundByPaymentId(payment.getId())
            .orElseGet(() -> refundRepository.save(Refund.requestedBySystem(
                payment,
                amount,
                reason,
                UUID.randomUUID().toString(),
                LocalDateTime.now(clock))));
    }
}
