package com.recipick.domain.payment.facade;

import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.service.PaymentApprovalOutcome;
import com.recipick.domain.payment.service.PaymentDelayQueue;
import com.recipick.domain.payment.service.PaymentDelayTask;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.domain.payment.service.RefundReconcileOutcome;
import com.recipick.global.exception.BusinessException;
import com.recipick.infra.portone.PortOnePaymentClient;
import com.recipick.infra.portone.dto.PortOneCancelOutcome;
import com.recipick.infra.portone.dto.PortOneCancelResult;
import com.recipick.infra.portone.dto.PortOneCancellation;
import com.recipick.infra.portone.dto.PortOneCancellationStatus;
import com.recipick.infra.portone.dto.PortOneCancellationTrigger;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

// SYSTEM 환불(만료 뒤 승인·금액 불일치·늦은 승인)을 실제로 돌려준다. Worker가 큐에서 꺼낸 SYSTEM_REFUND_EXECUTE:{id}마다 부른다.
// 트랜잭션을 걸지 않는다 — 스냅샷 조회(짧은 readOnly) → PortOne 조회·취소(락 없음) → SystemRefundProcessor.apply(PAYMENT 잠금) 순이다.
// 취소 POST 전에 항상 GET으로 이 환불의 취소가 이미 접수됐는지 본다. 웹훅·재실행·Fallback이 같은 환불을 여러 번 넘겨도
// 이미 보이는 취소는 다시 요청하지 않고, 동시에 요청돼도 환불 행마다 고정된 서버 키라 PortOne에서 한 건으로 처리된다.
// 콘솔·PortOne 관리자 화면에서 이미 돌려준 전액 취소는 POST 여부를 정하기 전에 공통 판정으로 먼저 본다 — 같은 돈을 두 번 돌려주지 않는다
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemRefundExecutionFacade {

    // 아직 미확정일 때 다음 실행까지. Fallback도 같은 시간이 지난 REQUESTED 환불을 복구 대상으로 고르므로 공개한다
    public static final Duration RECHECK_DELAY = Duration.ofMinutes(10);

    // requestedAt부터 이 시간이 지나면 고빈도 재실행을 멈추고 Fallback 주기에 맡긴다. 회원 환불 재확인과 같은 기준이다.
    // 실패 확정 기준이 아니고, 지나도 GET으로 취소가 없음을 확인하면 POST할 수 있다
    static final Duration RECHECK_WINDOW = Duration.ofHours(3);

    // 회원 취소(PaymentCancelFacade)와 같은 규칙. 환불 행 하나가 PortOne 취소 작업 하나다
    private static final String PORTONE_IDEMPOTENCY_KEY_PREFIX = "recipick-refund-";

    private final PaymentService paymentService;
    private final PortOnePaymentClient portOnePaymentClient;
    private final SystemRefundProcessor systemRefundProcessor;
    private final PaymentDelayQueue paymentDelayQueue;
    private final Clock clock;

    // 결제 확정(API·웹훅)이 Processor 커밋 뒤에 부른다. SYSTEM 환불을 만들 수 있는 판정이고 그 환불이 REQUESTED면
    // 바로 실행을 예약한다. Processor 트랜잭션 안에서 예약하지 않는다 — 롤백되면 없는 환불의 작업만 남고,
    // 커밋 전에 Worker가 꺼내면 아직 보이지 않는 환불이라 SKIPPED로 끝난다.
    // 예약 실패는 호출한 쪽의 결과를 바꾸지 않는다. 환불이 REQUESTED로 남아 Fallback이 다시 등록한다
    public void scheduleAfterConfirm(Long paymentId, PaymentApprovalOutcome outcome) {
        // 값을 받는 switch 식이라 판정이 늘면 컴파일이 실패한다. 그 판정이 SYSTEM 환불을 만드는지 그때 정한다
        boolean mayRequestRefund = switch (outcome) {
            case AMOUNT_MISMATCH, EXPIRED, ALREADY_CLOSED -> true;
            case APPROVED, ALREADY_PAID, NOT_PAID, PG_FAILED, ORDER_MISMATCH, INVALID_STATUS -> false;
        };
        if (!mayRequestRefund) {
            return;
        }
        try {
            // ALREADY_CLOSED는 결제·PG 상태나 PG 금액에 따라 환불을 만들지 않았을 수 있다. 이미 끝난 환불도 여기서 빠진다
            Optional<Refund> refund = paymentService.findRequestedSystemRefund(paymentId);
            if (refund.isEmpty()) {
                return;
            }
            Long refundId = refund.get().getId();
            paymentDelayQueue.enqueueIfEarlier(PaymentDelayTask.systemRefundExecute(refundId), clock.instant());
            log.info("SYSTEM 환불 실행 예약: paymentId={}, refundId={}, outcome={}", paymentId, refundId, outcome);
        } catch (DataAccessException e) {
            log.error("SYSTEM 환불 실행 예약 실패: paymentId={}, outcome={}", paymentId, outcome, e);
        }
    }

    public RefundReconcileOutcome execute(Long refundId) {
        // 스냅샷
        Optional<Refund> found;
        try {
            found = paymentService.findRefundWithPayment(refundId);
        } catch (TransientDataAccessException e) {
            log.warn("SYSTEM 환불 조회 중 일시적 DB 장애: refundId={}", refundId, e);
            scheduleRecheck(refundId, "환불 조회 일시 장애: " + e.getClass().getSimpleName());
            return RefundReconcileOutcome.RESCHEDULED;
        } catch (RuntimeException e) {
            log.error("SYSTEM 환불 조회 중 내부 오류: refundId={}", refundId, e);
            return RefundReconcileOutcome.MANUAL_REVIEW;
        }
        if (found.isEmpty()) {
            log.warn("실행할 SYSTEM 환불이 없음: refundId={}", refundId);
            return RefundReconcileOutcome.SKIPPED;
        }
        Refund refund = found.get();
        if (refund.getRequestedBy() != RefundRequester.SYSTEM) {
            // 등록하는 곳(확정·웹훅·Fallback)이 SYSTEM 환불만 넣으므로 여기 오면 등록 쪽 버그다
            log.warn("SYSTEM 환불이 아니라 실행하지 않음: refundId={}, requestedBy={}", refundId, refund.getRequestedBy());
            return RefundReconcileOutcome.SKIPPED;
        }
        if (refund.getStatus() != RefundStatus.REQUESTED) {
            log.info("이미 끝난 SYSTEM 환불이라 실행하지 않음: refundId={}, status={}", refundId, refund.getStatus());
            return RefundReconcileOutcome.SKIPPED;
        }

        Decision decision;
        try {
            decision = check(refund);
        } catch (TransientDataAccessException e) {
            // 락 대기 초과·쿼리 timeout처럼 다시 하면 풀릴 수 있는 DB 장애
            log.warn("일시적 DB 장애로 SYSTEM 환불 재실행: refundId={}", refundId, e);
            decision = Decision.retry("일시적 DB 장애: " + e.getClass().getSimpleName());
        } catch (RuntimeException e) {
            // 코드 버그·예상 못한 상태. 다시 해도 같은 결과라 예약하지 않는다. Fallback이 시간 단위로 다시 넣는다
            log.error("SYSTEM 환불 실행 내부 오류: refundId={}", refundId, e);
            decision = Decision.manualReview("예상하지 못한 내부 오류: " + e.getClass().getSimpleName());
        }
        return finish(refund, decision);
    }

    private Decision check(Refund refund) {
        Payment payment = refund.getPayment();
        // SYSTEM 환불은 실패로 닫힌 결제에서만 생긴다. 아니면 살아 있는 결제의 돈을 돌려주게 되므로 PortOne을 부르지 않는다
        if (payment.getStatus() != PaymentStatus.FAILED) {
            return Decision.manualReview("SYSTEM 환불 대상 결제가 FAILED가 아님: status=" + payment.getStatus());
        }

        // 락 없이 PortOne을 묻는다
        PortOnePayment portOnePayment;
        try {
            portOnePayment = portOnePaymentClient.getPayment(payment.getPortonePaymentId());
        } catch (BusinessException e) {
            // 404·5xx·timeout 모두 일시적일 수 있다. 클라이언트가 WARN을 남겼다
            return Decision.retry("PortOne 조회 실패: " + e.getErrorCode().getCode());
        }

        // POST 여부를 정하기 전에 외부 전액 취소를 먼저 본다. 웹훅·회원 환불 재확인과 같은 판정이다.
        // 환불에 기록된 취소 ID가 있으면 그 ID만 고정해 넘긴다 — 다른 취소로 다시 묶지 않고 곁의 취소와 충돌만 함께 본다.
        // 금액 기준은 결제가 아니라 환불이다. 금액 불일치로 생긴 SYSTEM 환불은 결제 금액과 다르다
        String cancellationId = refund.getPortoneCancellationId();
        ExternalCancellationMatch match =
            RefundCancellationRules.matchExternalFullCancellation(portOnePayment, refund.getAmount(), cancellationId);
        return switch (match.type()) {
            case MATCHED -> applyExisting(refund, portOnePayment, match.cancellation());
            case PENDING -> Decision.retry(match.reason());
            case CONFLICT -> Decision.manualReview(match.reason());
            case ABSENT -> reconcileApiCancellation(refund, portOnePayment, cancellationId);
        };
    }

    // 자동 정합화할 외부 취소가 없다. 서버가 API로 요청한 취소를 반영하거나, 없으면 취소를 요청한다
    private Decision reconcileApiCancellation(Refund refund, PortOnePayment portOnePayment, String cancellationId) {
        // 이 환불의 취소 내역을 고른다. 이미 받은 ID가 있으면 그것, 없으면 서버가 API로 요청한 취소 한 건.
        // 후보를 금액으로 미리 거르지 않는다 — 금액이 다른 API 취소가 후보에서 빠지면 "취소 없음"으로 보여 새 POST가 나간다.
        // 금액 검증은 applyExisting이 MANUAL_REVIEW로 막는다
        Optional<PortOneCancellation> cancellation;
        if (cancellationId != null) {
            cancellation = portOnePayment.cancellations().stream()
                .filter(candidate -> cancellationId.equals(candidate.id()))
                .findFirst();
            if (cancellation.isEmpty()) {
                // ID는 받았는데 조회 결과에 아직 없다. PortOne 반영이 늦는 경우라 POST하지 않고 다시 본다
                return Decision.retry("조회 결과에 취소 내역 없음: cancellationId=" + cancellationId);
            }
        } else {
            List<PortOneCancellation> apiCancellations = portOnePayment.cancellations().stream()
                .filter(candidate -> candidate.trigger() == PortOneCancellationTrigger.API)
                .toList();
            if (apiCancellations.size() > 1) {
                return Decision.manualReview("서버 API 취소 내역이 " + apiCancellations.size() + "건");
            }
            cancellation = apiCancellations.stream().findFirst();
        }

        if (cancellation.isPresent()) {
            return applyExisting(refund, portOnePayment, cancellation.get());
        }
        return requestCancel(refund, portOnePayment);
    }

    // 이미 접수된 취소가 보인다. 다시 POST하지 않고 그 결과를 반영한다
    private Decision applyExisting(Refund refund, PortOnePayment portOnePayment, PortOneCancellation cancellation) {
        if (!RefundCancellationRules.matchesAmount(cancellation, refund)) {
            return Decision.manualReview("취소 금액 불일치: refundAmount=" + refund.getAmount()
                + ", cancellationAmount=" + cancellation.totalAmount());
        }
        if (RefundCancellationRules.hasOtherSucceededCancellation(portOnePayment, cancellation)) {
            return Decision.manualReview("고른 취소 외에 성공한 취소가 또 있음: selected=" + cancellation.id());
        }
        // 지금 반영할 취소는 빼고, 그 밖에 API가 아닌 취소가 살아 있으면 결제 전체의 최종 취소 상태를 단정할 수 없어 운영에 넘긴다
        if (RefundCancellationRules.hasOtherActiveNonApiCancellation(portOnePayment, cancellation)) {
            return Decision.manualReview("API가 아닌 취소가 진행 중이거나 완료됨: selected=" + cancellation.id());
        }
        PortOneCancelResult result = cancellation.toCancelResult();
        RefundStatus status = systemRefundProcessor.apply(refund.getPayment().getId(), refund.getId(), result);
        log.info("SYSTEM 환불 기존 취소 반영: refundId={}, cancellationId={}, status={}",
            refund.getId(), cancellation.id(), status);
        // 판정과 잠금 사이에 다른 경로가 이 환불을 실패로 끝냈을 수 있다. PortOne에서는 취소가 성공했으므로
        // 정상 종료로 기록하지 않고 운영에 넘긴다 — 끝난 환불을 다른 취소로 다시 묶지 않는다
        if (cancellation.status() == PortOneCancellationStatus.SUCCEEDED && status == RefundStatus.FAILED) {
            return Decision.manualReview("PortOne 취소는 성공했지만 SYSTEM 환불이 FAILED 상태: refundId="
                + refund.getId() + ", cancellationId=" + cancellation.id());
        }
        return decide(status, "PortOne 취소 처리 중: cancellationId=" + cancellation.id());
    }

    // 이 환불의 취소가 아직 없다. 외부 취소는 앞선 공통 판정이 이미 반영하거나 막았다. PG에서 결제가 여전히 PAID일 때만 돌려준다 —
    // 부분 취소·취소된 결제를 근거 없이 다시 취소하지 않는다
    private Decision requestCancel(Refund refund, PortOnePayment portOnePayment) {
        // 앞선 판정이 걸러 정상 흐름에서는 걸리지 않지만, 다른 서버 키로 새 취소를 만드는 POST 직전의 마지막 방어선이라 남긴다
        if (RefundCancellationRules.hasActiveNonApiCancellation(portOnePayment)) {
            return Decision.manualReview("API가 아닌 취소가 진행 중이거나 완료됨");
        }
        if (portOnePayment.status() != PortOnePaymentStatus.PAID) {
            return Decision.manualReview("PG 결제가 PAID가 아닌데 이 환불의 취소 내역이 없음: status="
                + portOnePayment.status());
        }
        PortOneCancelResult result = portOnePaymentClient.cancelPaymentByAdmin(
            refund.getPayment().getPortonePaymentId(),
            PORTONE_IDEMPOTENCY_KEY_PREFIX + refund.getId(),
            refund.getReason(),
            refund.getAmount());
        RefundStatus status = systemRefundProcessor.apply(refund.getPayment().getId(), refund.getId(), result);
        log.info("SYSTEM 환불 취소 요청 반영: refundId={}, outcome={}, status={}",
            refund.getId(), result.outcome(), status);
        // PortOne POST 성공 뒤 PAYMENT 잠금을 기다리는 사이 다른 경로가 이 환불을 실패로 끝냈을 수 있다.
        // PG에서는 취소가 완료됐으므로 정상 종료로 기록하지 않고 운영에 넘긴다
        if (result.outcome() == PortOneCancelOutcome.SUCCEEDED && status == RefundStatus.FAILED) {
            return Decision.manualReview("PortOne 취소는 성공했지만 SYSTEM 환불이 FAILED 상태: refundId="
                + refund.getId() + ", cancellationId=" + result.cancellationId());
        }
        return decide(status, "취소 결과 미확정: outcome=" + result.outcome() + ", detail=" + result.detail());
    }

    // 환불이 끝났으면(COMPLETED·FAILED) 이 작업도 끝이다. REQUESTED로 남았으면 다시 실행한다
    private Decision decide(RefundStatus status, String pendingReason) {
        return status == RefundStatus.REQUESTED ? Decision.retry(pendingReason) : Decision.resolved();
    }

    // 환불 행은 REQUESTED·COMPLETED·FAILED만 갖는다. 재실행·수동 확인은 상태로 남기지 않고 예약과 로그로만 표현한다
    private RefundReconcileOutcome finish(Refund refund, Decision decision) {
        return switch (decision.outcome()) {
            case RESOLVED -> RefundReconcileOutcome.RESOLVED;
            case MANUAL_REVIEW -> {
                log.error("SYSTEM 환불 수동 확인 필요: refundId={}, paymentId={}, reason={}",
                    refund.getId(), refund.getPayment().getId(), decision.reason());
                yield RefundReconcileOutcome.MANUAL_REVIEW;
            }
            case RESCHEDULED -> {
                LocalDateTime now = LocalDateTime.now(clock);
                if (!now.isBefore(refund.getRequestedAt().plus(RECHECK_WINDOW))) {
                    // 자동으로 실패 처리하지 않는다. 운영이 보도록 남기고 Fallback이 시간 단위로 다시 넣는다
                    log.error("SYSTEM 환불이 {}시간 넘게 미확정: refundId={}, paymentId={}, reason={}",
                        RECHECK_WINDOW.toHours(), refund.getId(), refund.getPayment().getId(), decision.reason());
                    yield RefundReconcileOutcome.STALLED;
                }
                scheduleRecheck(refund.getId(), decision.reason());
                yield RefundReconcileOutcome.RESCHEDULED;
            }
            case SKIPPED, STALLED -> throw new IllegalStateException("실행 판정은 " + decision.outcome() + "가 될 수 없습니다.");
        };
    }

    // 같은 환불을 확정·웹훅·Fallback도 등록하므로 더 이른 예약을 늦추지 않는 enqueueIfEarlier를 쓴다
    private void scheduleRecheck(Long refundId, String reason) {
        Instant executeAt = clock.instant().plus(RECHECK_DELAY);
        try {
            paymentDelayQueue.enqueueIfEarlier(PaymentDelayTask.systemRefundExecute(refundId), executeAt);
            log.info("SYSTEM 환불 재실행 예약: refundId={}, executeAt={}, reason={}", refundId, executeAt, reason);
        } catch (DataAccessException e) {
            // 환불은 REQUESTED 그대로라 Fallback이 다시 등록한다
            log.error("SYSTEM 환불 재실행 예약 실패: refundId={}", refundId, e);
        }
    }

    // 실행 판정과 로그용 사유
    private record Decision(RefundReconcileOutcome outcome, String reason) {

        static Decision resolved() {
            return new Decision(RefundReconcileOutcome.RESOLVED, null);
        }

        static Decision retry(String reason) {
            return new Decision(RefundReconcileOutcome.RESCHEDULED, reason);
        }

        static Decision manualReview(String reason) {
            return new Decision(RefundReconcileOutcome.MANUAL_REVIEW, reason);
        }
    }
}
