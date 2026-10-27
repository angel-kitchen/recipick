package com.recipick.domain.payment.service;

import com.recipick.domain.payment.dto.PaymentCreated;
import com.recipick.domain.payment.dto.RefundSummary;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.repository.PaymentRepository;
import com.recipick.domain.payment.repository.RefundRepository;
import com.recipick.domain.payment.repository.dto.PaymentAdminSearchCondition;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

    // 주문 생성 재시도 응답용. 결제 엔티티를 주문 도메인으로 넘기지 않도록 변환까지 여기서 끝낸다
    public PaymentCreated getCreatedByOrderId(Long orderId) {
        return PaymentCreated.from(getByOrderId(orderId));
    }

    // 관리자 결제 상세용. 상태를 바꾸지 않으므로 잠그지 않는다
    public Payment getById(Long paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    // 결제 만료 작업의 잠금 없는 스냅샷. 큐에 남은 오래된 작업은 결제가 없을 수 있어 예외 대신 빈 값을 돌려준다.
    // 상태 변경은 각 Processor가 PAYMENT 잠금 아래에서 다시 읽어 판단한다
    public Optional<Payment> findById(Long paymentId) {
        return paymentRepository.findById(paymentId);
    }

    // 상태를 바꾸는 흐름의 첫 줄에서 부른다. 호출자 트랜잭션에 참여해야 잠금이 유지된다
    @Transactional
    public Payment getByOrderIdForUpdate(Long orderId) {
        return paymentRepository.findByOrderIdForUpdate(orderId)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    // 취소 흐름의 첫 줄. 결제 ID로 들어오는 API라 orderId 잠금 조회와 따로 둔다
    @Transactional
    public Payment getByIdForUpdate(Long paymentId) {
        return paymentRepository.findByIdForUpdate(paymentId)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    // 웹훅은 PortOne 결제 ID로 들어온다. 없으면 레시픽 결제가 아니므로 예외 대신 빈 값을 돌려주고 호출자가 IGNORED로 적는다
    public Optional<Payment> findByPortonePaymentId(String portonePaymentId) {
        return paymentRepository.findByPortonePaymentId(portonePaymentId);
    }

    // 정합화의 환불 찾기 1. portone_cancellation_id가 UNIQUE라 최대 한 건이다.
    // 잠금 없는 스냅샷이고 최종 판정은 PaymentCancelProcessor.apply가 PAYMENT 잠금 아래에서 다시 읽어 한다
    public Optional<Refund> findRefundByPortoneCancellationId(String portoneCancellationId) {
        return refundRepository.findByPortoneCancellationId(portoneCancellationId);
    }

    // 정합화의 환불 찾기 2. 취소 ID를 아직 못 받은 회원 환불. 정확히 한 건일 때만 자동 반영하므로 판단은 호출자가 한다
    public List<Refund> findRequestedMemberRefunds(Long paymentId) {
        return refundRepository.findRequestedMemberRefundsByPaymentId(paymentId);
    }

    // SYSTEM 환불 실행 예약·취소 웹훅용. 한 결제에 SYSTEM 환불은 최대 한 건이라(requestSystemRefund) 아직 REQUESTED인 것만 돌려준다.
    // 취소 웹훅이 트랜잭션 밖에서 refund.getPayment()를 읽으므로 결제를 함께 조회한다.
    // 잠금 없는 스냅샷이고 최종 판정은 SystemRefundProcessor.apply가 PAYMENT 잠금 아래에서 다시 읽어 한다
    public Optional<Refund> findRequestedSystemRefund(Long paymentId) {
        return refundRepository.findSystemRefundByPaymentId(paymentId)
            .filter(refund -> refund.getStatus() == RefundStatus.REQUESTED);
    }

    // 환불 재확인용. 결제를 함께 읽어 트랜잭션 밖에서 결제 ID·PortOne 결제 ID를 쓸 수 있다. 잠금 없는 스냅샷이다
    public Optional<Refund> findRefundWithPayment(Long refundId) {
        return refundRepository.findWithPaymentById(refundId);
    }

    // Fallback Sweep용. requestedBefore 이전에 요청된 회원 REQUESTED 환불 ID를 afterId 다음부터 limit건 읽는다
    public List<Long> findStaleMemberRefundIds(LocalDateTime requestedBefore, Long afterId, int limit) {
        return refundRepository.findStaleRequestedMemberRefundIds(afterId, requestedBefore, PageRequest.ofSize(limit));
    }

    // SYSTEM 환불 Fallback Sweep용. requestedBefore 이전에 요청된 SYSTEM REQUESTED 환불 ID를 afterId 다음부터 limit건 읽는다
    public List<Long> findStaleSystemRefundIds(LocalDateTime requestedBefore, Long afterId, int limit) {
        return refundRepository.findStaleRequestedSystemRefundIds(afterId, requestedBefore, PageRequest.ofSize(limit));
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

    // 결제 후 취소의 같은 키 재요청 판정. PAYMENT를 잠근 TX1 안에서만 뜻이 있으므로 바깥 트랜잭션을 강제한다
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Refund> findRefundByIdempotencyKey(String idempotencyKey) {
        return refundRepository.findByIdempotencyKey(idempotencyKey);
    }

    // TX2가 PAYMENT를 잠근 뒤 TX1이 만든 환불 행을 새 영속성 컨텍스트에서 다시 읽는다.
    // 환불 변경은 모두 PAYMENT 잠금 아래에서 하므로 환불 행은 따로 잠그지 않는다. 없으면 호출자 버그다
    @Transactional(propagation = Propagation.MANDATORY)
    public Refund getRefundById(Long refundId) {
        return refundRepository.findById(refundId)
            .orElseThrow(() -> new IllegalStateException("환불 행이 없습니다. refundId=" + refundId));
    }

    // 회원 환불은 환불 행이 곧 멱등 작업이라 클라이언트 키를 그대로 저장한다. PAYMENT·ORDER의 CANCEL_REQUESTED와 한 트랜잭션에
    // 묶여야 하므로 바깥 트랜잭션(TX1)을 강제한다. 같은 키 선조회는 PAYMENT 잠금 아래에서 끝났지만, 다른 결제에 같은 키가
    // 동시에 들어오면 서로 다른 PAYMENT를 잠가 둘 다 통과하므로 UNIQUE가 최종 판정한다. 즉시 flush해 충돌을 여기서 받는다.
    // 새 MEMBER 환불의 non-null UNIQUE 값은 idempotencyKey뿐이다 — portoneCancellationId는 아직 null이라 이 충돌은 키 경쟁이다
    @Transactional(propagation = Propagation.MANDATORY)
    public Refund requestMemberRefund(Payment payment, String reason, String idempotencyKey) {
        try {
            return refundRepository.saveAndFlush(
                Refund.requestedByMember(payment, reason, idempotencyKey, LocalDateTime.now(clock)));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(CommonErrorCode.IDEMPOTENCY_KEY_PAYLOAD_MISMATCH, e);
        }
    }

    // 내 결제 내역. 정렬은 호출자가 넘긴 Pageable이 정한다
    public Page<Payment> findHistory(Collection<Long> orderIds, Pageable pageable) {
        return paymentRepository.findByOrderIdIn(orderIds, pageable);
    }

    // 관리자 결제 목록. 회원 ID → 주문 ID 변환은 Facade가 주문 Service로 끝내고 조건만 넘긴다
    public Page<Payment> searchForAdmin(PaymentAdminSearchCondition condition, Pageable pageable) {
        return paymentRepository.searchForAdmin(condition, pageable);
    }

    // 주문 결제 조회용 단건 요약
    public RefundSummary getRefundSummary(Long paymentId) {
        return summarize(refundRepository.findAllByPaymentIdOrderByIdDesc(paymentId));
    }

    // 관리자 결제 상세용 환불 전체 이력. 실패 후 재시도한 시도까지 모두, 최신 시도부터 돌려준다
    public List<Refund> findRefundsOf(Long paymentId) {
        return refundRepository.findAllByPaymentIdOrderByIdDesc(paymentId);
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
