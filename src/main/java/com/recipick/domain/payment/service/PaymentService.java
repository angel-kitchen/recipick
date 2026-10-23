package com.recipick.domain.payment.service;

import com.recipick.domain.payment.dto.RefundSummary;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.repository.PaymentRepository;
import com.recipick.domain.payment.repository.RefundRepository;
import com.recipick.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // 내 결제 내역. 정렬은 호출자가 넘긴 Pageable이 정한다
    public Page<Payment> findHistory(Collection<Long> orderIds, Pageable pageable) {
        return paymentRepository.findByOrderIdIn(orderIds, pageable);
    }

    // 주문 결제 조회용 단건 요약
    public RefundSummary getRefundSummary(Long paymentId) {
        return summarize(refundRepository.findAllByPaymentIdOrderByIdDesc(paymentId));
    }

    // 결제 내역용 배치 요약. 결제마다 부르면 페이지 크기만큼 쿼리가 늘어나므로 한 번에 읽어 여기서 나눈다.
    // 환불이 없는 결제는 Map에 키가 없으니 호출자가 NONE으로 채운다
    public Map<Long, RefundSummary> getRefundSummaries(Collection<Long> paymentIds) {
        if (paymentIds.isEmpty()) {
            return Map.of();
        }
        return refundRepository.findAllByPaymentIdIn(paymentIds).stream()
            .collect(Collectors.groupingBy(refund -> refund.getPayment().getId()))
            .entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> summarize(entry.getValue())));
    }

    // 완료 금액은 COMPLETED 합계다 — 금액 불일치 경로는 PG 청구액으로 적히고 관리자 재처리는 별도 행이라 한 건만 집을 수 없다.
    // 최근 상태는 id가 가장 큰 행이다. 배치 조회는 정렬 없이 읽어 오므로 순서에 기대지 않고 여기서 직접 찾는다
    private RefundSummary summarize(List<Refund> refunds) {
        if (refunds.isEmpty()) {
            return RefundSummary.NONE;
        }
        long completedAmount = refunds.stream()
            .filter(refund -> refund.getStatus() == RefundStatus.COMPLETED)
            .mapToLong(Refund::getAmount)
            .sum();
        RefundStatus latestStatus = refunds.stream()
            .max(Comparator.comparing(Refund::getId))
            .map(Refund::getStatus)
            .orElseThrow();
        return new RefundSummary(completedAmount, latestStatus);
    }
}
