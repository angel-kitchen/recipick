package com.recipick.domain.payment.facade;

import com.recipick.domain.payment.dto.PaymentCancelResult;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.service.PaymentCancelOutcome;
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
import com.recipick.infra.portone.dto.PortOnePayment;
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

// 결과가 확정되지 않은 회원 환불(REQUESTED)을 PortOne 재조회로 마무리한다. Worker가 큐에서 꺼낸 REFUND_RECONCILE:{id}마다 부른다.
// 취소를 다시 요청하는 기능이 아니라 이미 시작된 취소의 현재 상태를 다시 묻는 기능이다 — PortOne 취소 POST는 부르지 않는다.
// 콘솔·PortOne 관리자 화면의 외부 전액 취소는 API 취소보다 먼저 공통 판정(RefundCancellationRules)으로 본다.
// 트랜잭션을 걸지 않는다 — 스냅샷 조회(짧은 readOnly) → PortOne 조회(락 없음) → PaymentCancelProcessor.apply(PAYMENT 잠금) 순이다.
// 환불 행에는 시도 횟수 컬럼이 없어 REQUESTED 시각부터 잰 시간으로 고빈도 재확인을 멈춘다. 그 뒤는 Fallback이 시간 단위로 다시 넣는다
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundReconciliationFacade {

    // 취소 API·웹훅이 PENDING을 남긴 뒤 첫 재확인까지. PortOne 비동기 취소가 보통 이 안에 끝난다
    public static final Duration INITIAL_DELAY = Duration.ofMinutes(5);

    // 아직 미확정일 때 다음 재확인까지
    static final Duration RECHECK_DELAY = Duration.ofMinutes(10);

    // requestedAt부터 이 시간이 지나면 고빈도 재확인을 멈춘다. 취소 API의 PortOne 멱등 보장 시간(3시간)에 맞췄다.
    // 실패 확정 기준이 아니다 — 지나도 FAILED·PAID 복귀·재취소 POST를 하지 않는다
    static final Duration RECHECK_WINDOW = Duration.ofHours(3);

    private final PaymentService paymentService;
    private final PortOnePaymentClient portOnePaymentClient;
    private final PaymentCancelProcessor paymentCancelProcessor;
    private final PaymentDelayQueue paymentDelayQueue;
    private final Clock clock;

    public RefundReconcileOutcome reconcile(Long refundId) {
        // 스냅샷이다. 최종 판정은 apply가 PAYMENT 잠금 아래에서 환불을 다시 읽어 한다.
        // 조회 자체가 실패하면 requestedAt을 몰라 3시간 경계를 볼 수 없다 — 일시적 장애면 한 번 더 예약만 한다
        Optional<Refund> found;
        try {
            found = paymentService.findRefundWithPayment(refundId);
        } catch (TransientDataAccessException e) {
            log.warn("환불 조회 중 일시적 DB 장애: refundId={}", refundId, e);
            scheduleRecheck(refundId, "환불 조회 일시 장애: " + e.getClass().getSimpleName());
            return RefundReconcileOutcome.RESCHEDULED;
        } catch (RuntimeException e) {
            log.error("환불 조회 중 내부 오류: refundId={}", refundId, e);
            return RefundReconcileOutcome.MANUAL_REVIEW;
        }
        if (found.isEmpty()) {
            log.warn("재확인할 환불이 없음: refundId={}", refundId);
            return RefundReconcileOutcome.SKIPPED;
        }
        Refund refund = found.get();
        if (refund.getStatus() != RefundStatus.REQUESTED) {
            // 웹훅이나 다른 재확인이 먼저 끝냈다
            log.info("이미 끝난 환불이라 재확인하지 않음: refundId={}, status={}", refundId, refund.getStatus());
            return RefundReconcileOutcome.SKIPPED;
        }
        if (refund.getRequestedBy() != RefundRequester.MEMBER) {
            // apply는 회원 환불만 받는다. SYSTEM 환불은 SystemRefundExecutionFacade가 맡는다.
            // 등록하는 곳(취소 API·웹훅·Fallback)이 회원 환불만 넣으므로 여기 오면 등록 쪽 버그다
            log.warn("회원 환불이 아니라 재확인하지 않음: refundId={}, requestedBy={}", refundId, refund.getRequestedBy());
            return RefundReconcileOutcome.SKIPPED;
        }

        Decision decision;
        try {
            decision = check(refund);
        } catch (TransientDataAccessException e) {
            // 락 대기 초과·쿼리 timeout처럼 다시 하면 풀릴 수 있는 DB 장애
            log.warn("일시적 DB 장애로 환불 재확인 재시도: refundId={}", refundId, e);
            decision = Decision.retry("일시적 DB 장애: " + e.getClass().getSimpleName());
        } catch (RuntimeException e) {
            // 코드 버그·예상 못한 상태. 다시 해도 같은 결과라 예약하지 않는다. Fallback이 시간 단위로 다시 넣는다
            log.error("환불 재확인 내부 오류: refundId={}", refundId, e);
            decision = Decision.manualReview("예상하지 못한 내부 오류: " + e.getClass().getSimpleName());
        }
        return finish(refund, decision);
    }

    private Decision check(Refund refund) {
        Payment payment = refund.getPayment();

        // 락 없이 PortOne을 묻는다
        PortOnePayment portOnePayment;
        try {
            portOnePayment = portOnePaymentClient.getPayment(payment.getPortonePaymentId());
        } catch (BusinessException e) {
            // 404·5xx·timeout 모두 일시적일 수 있다. 클라이언트가 WARN을 남겼다
            return Decision.retry("PortOne 조회 실패: " + e.getErrorCode().getCode());
        }

        // 외부 전액 취소를 먼저 본다. 웹훅·SYSTEM 환불 실행과 같은 판정이라 어느 경로가 먼저 보든 결과가 같다.
        // 환불에 기록된 취소 ID가 있으면 그 ID만 고정해 넘긴다 — 다른 취소로 다시 묶지 않고 곁의 취소와 충돌만 함께 본다
        String cancellationId = refund.getPortoneCancellationId();
        ExternalCancellationMatch match =
            RefundCancellationRules.matchExternalFullCancellation(portOnePayment, refund.getAmount(), cancellationId);
        return switch (match.type()) {
            case MATCHED -> applyCancellation(refund, portOnePayment, match.cancellation());
            case PENDING -> Decision.retry(match.reason());
            case CONFLICT -> Decision.manualReview(match.reason());
            case ABSENT -> reconcileApiCancellation(refund, portOnePayment, cancellationId);
        };
    }

    // 자동 정합화할 외부 취소가 없다. 서버가 API로 요청한 취소를 이 환불에 묶는 기존 판정이다
    private Decision reconcileApiCancellation(Refund refund, PortOnePayment portOnePayment, String cancellationId) {
        // 이 환불의 취소 내역을 고른다. 취소 API·웹훅이 받은 ID가 있으면 그것, 없으면 서버가 요청한 전액 취소 한 건
        PortOneCancellation cancellation;
        if (cancellationId != null) {
            Optional<PortOneCancellation> byId = portOnePayment.cancellations().stream()
                .filter(candidate -> cancellationId.equals(candidate.id()))
                .findFirst();
            if (byId.isEmpty()) {
                // ID는 받았는데 조회 결과에 아직 없다. PortOne 반영이 늦는 경우다
                return Decision.retry("조회 결과에 취소 내역 없음: cancellationId=" + cancellationId);
            }
            cancellation = byId.get();
        } else {
            // 취소 API가 응답을 못 받아 ID가 없다. 서버가 요청한 전액 취소가 정확히 한 건일 때만 그것으로 본다
            List<PortOneCancellation> candidates = portOnePayment.cancellations().stream()
                .filter(candidate -> RefundCancellationRules.isServerFullCancellation(candidate, refund))
                .toList();
            if (candidates.isEmpty()) {
                // PortOne에 아직 안 보이거나 요청이 닿지 않았다. 재취소 POST는 하지 않고 계속 지켜본다
                return Decision.retry("서버가 요청한 전액 취소 내역이 아직 없음");
            }
            if (candidates.size() > 1) {
                return Decision.manualReview("서버가 요청한 전액 취소 내역이 " + candidates.size() + "건");
            }
            cancellation = candidates.get(0);
        }
        return applyCancellation(refund, portOnePayment, cancellation);
    }

    // 고른 취소 하나를 이 환불에 반영한다. 외부 취소·API 취소 모두 이 경로 하나로 상태를 바꾼다
    private Decision applyCancellation(Refund refund, PortOnePayment portOnePayment, PortOneCancellation cancellation) {
        Payment payment = refund.getPayment();
        if (!RefundCancellationRules.matchesAmount(cancellation, refund)) {
            return Decision.manualReview("취소 금액 불일치: refundAmount=" + refund.getAmount()
                + ", cancellationAmount=" + cancellation.totalAmount());
        }
        if (RefundCancellationRules.hasOtherSucceededCancellation(portOnePayment, cancellation)) {
            return Decision.manualReview("고른 취소 외에 성공한 취소가 또 있음: selected=" + cancellation.id());
        }

        // 취소 API의 TX2를 그대로 쓴다. 끝난 환불은 apply가 바꾸지 않고 그 결과를 돌려준다
        PortOneCancelResult result = cancellation.toCancelResult();
        PaymentCancelResult applied = paymentCancelProcessor.apply(payment.getId(), refund.getId(), result);
        log.info("환불 재확인 반영: refundId={}, cancellationId={}, outcome={}",
            refund.getId(), cancellation.id(), applied.outcome());
        // 판정과 잠금 사이에 다른 경로가 이 환불을 실패로 끝냈을 수 있다. PortOne에서는 취소가 성공했으므로
        // 정상 종료로 기록하지 않고 운영에 넘긴다 — 끝난 환불을 다른 취소로 다시 묶지 않는다
        if (cancellation.status() == PortOneCancellationStatus.SUCCEEDED
            && (applied.outcome() == PaymentCancelOutcome.PG_FAILED
            || applied.outcome() == PaymentCancelOutcome.REQUEST_REJECTED)) {
            return Decision.manualReview("PortOne 취소는 성공했지만 회원 환불이 실패 상태로 끝남: refundId="
                + refund.getId() + ", cancellationId=" + cancellation.id() + ", outcome=" + applied.outcome());
        }
        return switch (applied.outcome()) {
            case CANCELED, PG_FAILED, REQUEST_REJECTED -> Decision.resolved();
            // PortOne이 아직 처리 중이다. apply가 취소 ID를 기록했으니 다음 재확인은 ID로 찾는다
            case PENDING -> result.outcome() == PortOneCancelOutcome.REQUESTED
                ? Decision.retry("PortOne 취소 처리 중: cancellationId=" + cancellation.id())
                : Decision.manualReview("취소 결과 확정 불가: result=" + result.outcome()
                                        + ", status=" + cancellation.status() + ", cancellationId=" + cancellation.id()
                                        + ", canceledAt=" + cancellation.canceledAt());
            case CANCELED_BEFORE_PAY -> throw new IllegalStateException(
                "결제 후 취소 반영에서 나올 수 없는 결과: " + applied.outcome());
        };
    }

    // 환불 행은 REQUESTED·COMPLETED·FAILED만 갖는다. 재시도·수동 확인은 상태로 남기지 않고 예약과 로그로만 표현한다
    private RefundReconcileOutcome finish(Refund refund, Decision decision) {
        return switch (decision.outcome()) {
            case RESOLVED -> RefundReconcileOutcome.RESOLVED;
            case MANUAL_REVIEW -> {
                log.error("환불 수동 확인 필요: refundId={}, paymentId={}, reason={}",
                    refund.getId(), refund.getPayment().getId(), decision.reason());
                yield RefundReconcileOutcome.MANUAL_REVIEW;
            }
            case RESCHEDULED -> {
                LocalDateTime now = LocalDateTime.now(clock);
                if (!now.isBefore(refund.getRequestedAt().plus(RECHECK_WINDOW))) {
                    // 자동으로 실패 처리하지 않는다. 운영이 보도록 남기고 Fallback이 시간 단위로 다시 넣는다
                    log.error("환불이 {}시간 넘게 미확정: refundId={}, paymentId={}, reason={}",
                        RECHECK_WINDOW.toHours(), refund.getId(), refund.getPayment().getId(), decision.reason());
                    yield RefundReconcileOutcome.STALLED;
                }
                scheduleRecheck(refund.getId(), decision.reason());
                yield RefundReconcileOutcome.RESCHEDULED;
            }
            case SKIPPED, STALLED -> throw new IllegalStateException("재확인 판정은 " + decision.outcome() + "가 될 수 없습니다.");
        };
    }

    // 같은 환불을 취소 API·웹훅·Fallback도 등록하므로 더 이른 예약을 늦추지 않는 enqueueIfEarlier를 쓴다
    private void scheduleRecheck(Long refundId, String reason) {
        Instant executeAt = clock.instant().plus(RECHECK_DELAY);
        try {
            paymentDelayQueue.enqueueIfEarlier(PaymentDelayTask.refundReconcile(refundId), executeAt);
            log.info("환불 재확인 예약: refundId={}, executeAt={}, reason={}", refundId, executeAt, reason);
        } catch (DataAccessException e) {
            // 환불은 REQUESTED 그대로라 Fallback이 다시 등록한다
            log.error("환불 재확인 예약 실패: refundId={}", refundId, e);
        }
    }

    // 재확인 판정과 로그용 사유
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
