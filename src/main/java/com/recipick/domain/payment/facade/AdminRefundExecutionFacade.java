package com.recipick.domain.payment.facade;

import com.recipick.domain.payment.dto.AdminRefundExecution;
import com.recipick.domain.payment.dto.MartRejectionPaymentResult;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.entity.enums.RefundStatus;
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
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// 마트 거절 환불(ADMIN)을 실제로 돌려준다. 거절 API·Worker(ADMIN_REFUND_EXECUTE:{id})·Fallback이 모두 이 실행기를 거친다.
// 트랜잭션을 걸지 않는다 — 스냅샷 조회(짧은 readOnly) → PortOne 조회·취소(락 없음) → MartRejectionProcessor.apply(PAYMENT 잠금) 순이다.
// 취소 POST 전에 항상 GET으로 이 환불의 취소가 이미 접수됐는지 본다. 같은 환불을 여러 경로가 넘겨도 보이는 취소는 다시 요청하지 않고,
// 동시에 요청돼도 환불 행마다 고정된 서버 키라 PortOne에서 한 건으로 처리된다.
// SYSTEM 환불 실행과 뼈대가 같지만 ID 없는 환불이 PortOne 취소를 고를 때 두 가지를 더 본다 — 다른 환불에 이미 묶인 취소는 후보가 아니고,
// 이 환불보다 먼저 요청된 취소는 이 환불의 것이 아니다. 같은 결제에 과거 회원 취소가 남아 있을 수 있어서다
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminRefundExecutionFacade {

    // 아직 미확정일 때 다음 실행까지. Fallback도 같은 시간이 지난 REQUESTED 환불을 복구 대상으로 고르므로 공개한다
    public static final Duration RECHECK_DELAY = Duration.ofMinutes(10);

    // requestedAt부터 이 시간이 지나면 고빈도 재실행을 멈추고 Fallback 주기에 맡긴다. 실패 확정 기준이 아니다
    static final Duration RECHECK_WINDOW = Duration.ofHours(3);

    // 회원 취소·SYSTEM 환불과 같은 규칙. 환불 행 하나가 PortOne 취소 작업 하나다
    private static final String PORTONE_IDEMPOTENCY_KEY_PREFIX = "recipick-refund-";

    private final PaymentService paymentService;
    private final PortOnePaymentClient portOnePaymentClient;
    private final MartRejectionProcessor martRejectionProcessor;
    private final PaymentDelayQueue paymentDelayQueue;
    private final Clock clock;

    // 거절 접수(TX1) 커밋 직후 거절 API가 부른다. 이어지는 동기 실행이 서버 중단 등으로 끝나지 못해도 10분 뒤 Worker가 이어받는다.
    // 예약 실패는 거절 접수 결과를 바꾸지 않는다. 환불이 REQUESTED로 남아 Fallback이 다시 등록한다
    public void scheduleSafetyNet(Long refundId) {
        scheduleRecheck(refundId, "거절 접수 직후 안전망");
    }

    public AdminRefundExecution execute(Long refundId) {
        // 스냅샷
        Optional<Refund> found;
        try {
            found = paymentService.findRefundWithPayment(refundId);
        } catch (TransientDataAccessException e) {
            log.warn("마트 거절 환불 조회 중 일시적 DB 장애: refundId={}", refundId, e);
            scheduleRecheck(refundId, "환불 조회 일시 장애: " + e.getClass().getSimpleName());
            return AdminRefundExecution.notApplied(RefundReconcileOutcome.RESCHEDULED);
        } catch (RuntimeException e) {
            log.error("마트 거절 환불 조회 중 내부 오류: refundId={}", refundId, e);
            return AdminRefundExecution.notApplied(RefundReconcileOutcome.MANUAL_REVIEW);
        }
        if (found.isEmpty()) {
            log.warn("실행할 마트 거절 환불이 없음: refundId={}", refundId);
            return AdminRefundExecution.notApplied(RefundReconcileOutcome.SKIPPED);
        }
        Refund refund = found.get();
        if (refund.getRequestedBy() != RefundRequester.ADMIN) {
            // 등록하는 곳(거절 API·Fallback)이 ADMIN 환불만 넣으므로 여기 오면 등록 쪽 버그다
            log.warn("마트 거절 환불이 아니라 실행하지 않음: refundId={}, requestedBy={}", refundId, refund.getRequestedBy());
            return AdminRefundExecution.notApplied(RefundReconcileOutcome.SKIPPED);
        }
        if (refund.getStatus() != RefundStatus.REQUESTED) {
            log.info("이미 끝난 마트 거절 환불이라 실행하지 않음: refundId={}, status={}", refundId, refund.getStatus());
            return AdminRefundExecution.notApplied(RefundReconcileOutcome.SKIPPED);
        }

        Decision decision;
        try {
            decision = check(refund);
        } catch (TransientDataAccessException e) {
            // 락 대기 초과·쿼리 timeout처럼 다시 하면 풀릴 수 있는 DB 장애. POST가 이미 나갔어도 다음 GET이 그 취소를 찾는다
            log.warn("일시적 DB 장애로 마트 거절 환불 재실행: refundId={}", refundId, e);
            decision = Decision.retry("일시적 DB 장애: " + e.getClass().getSimpleName());
        } catch (RuntimeException e) {
            // 코드 버그·TX2의 상태 불일치·바인딩 경합. 다시 해도 같은 결과라 예약하지 않는다. Fallback이 시간 단위로 다시 넣는다
            log.error("마트 거절 환불 실행 내부 오류: refundId={}", refundId, e);
            decision = Decision.manualReview("예상하지 못한 내부 오류: " + e.getClass().getSimpleName());
        }
        return finish(refund, decision);
    }

    private Decision check(Refund refund) {
        Payment payment = refund.getPayment();
        // 진행 중인 마트 거절 환불은 거절 접수로 CANCEL_REQUESTED가 된 결제에만 있다. 아니면 DB 정합성이 이미 깨져 PortOne을 부르지 않는다
        if (payment.getStatus() != PaymentStatus.CANCEL_REQUESTED) {
            return Decision.manualReview("마트 거절 환불 대상 결제가 CANCEL_REQUESTED가 아님: status=" + payment.getStatus());
        }

        // 락 없이 PortOne을 묻는다
        PortOnePayment portOnePayment;
        try {
            portOnePayment = portOnePaymentClient.getPayment(payment.getPortonePaymentId());
        } catch (BusinessException e) {
            // 404·5xx·timeout 모두 일시적일 수 있다. 클라이언트가 WARN을 남겼다
            return Decision.retry("PortOne 조회 실패: " + e.getErrorCode().getCode());
        }

        // POST 여부를 정하기 전에 콘솔·PortOne 관리자 화면의 전액 취소를 먼저 본다. 운영자가 먼저 돌려줬으면 그 취소로 이 환불을 끝낸다.
        // 환불에 기록된 취소 ID가 있으면 그 ID만 고정해 넘긴다
        String cancellationId = refund.getPortoneCancellationId();
        ExternalCancellationMatch match =
            RefundCancellationRules.matchExternalFullCancellation(portOnePayment, refund.getAmount(), cancellationId);
        return switch (match.type()) {
            case MATCHED -> applyExisting(refund, portOnePayment, match.cancellation());
            case PENDING -> Decision.retry(match.reason());
            case CONFLICT -> Decision.manualReview(match.reason());
            case ABSENT -> cancellationId != null
                ? trackCancellation(refund, portOnePayment, cancellationId)
                : reconcileApiCancellation(refund, portOnePayment);
        };
    }

    // 이미 받은 취소 ID가 있다. 다른 취소로 갈아타지 않고 그 ID만 본다
    private Decision trackCancellation(Refund refund, PortOnePayment portOnePayment, String cancellationId) {
        Optional<PortOneCancellation> cancellation = portOnePayment.cancellations().stream()
            .filter(candidate -> cancellationId.equals(candidate.id()))
            .findFirst();
        if (cancellation.isEmpty()) {
            // ID는 받았는데 조회 결과에 아직 없다. PortOne 반영이 늦는 경우라 POST하지 않고 다시 본다
            return Decision.retry("조회 결과에 취소 내역 없음: cancellationId=" + cancellationId);
        }
        return applyExisting(refund, portOnePayment, cancellation.get());
    }

    // 취소 ID를 아직 못 받았다(POST 응답 유실 등). 서버 API 취소 중 이 환불의 것을 고르거나, 없으면 취소를 요청한다.
    // 이 환불의 것이라고 확신할 수 없는 API 취소가 하나라도 살아 있으면 어느 것도 고르지 않는다 — 잘못 묶으면 되돌릴 수 없다.
    // 후보를 금액으로 미리 거르지 않는다 — 금액이 다른 API 취소가 빠지면 "취소 없음"으로 보여 새 POST가 나간다. 금액은 applyExisting이 막는다
    private Decision reconcileApiCancellation(Refund refund, PortOnePayment portOnePayment) {
        Set<String> boundIds = paymentService.findBoundCancellationIds(refund.getPayment().getId(), refund.getId());
        List<PortOneCancellation> apiCancellations = portOnePayment.cancellations().stream()
            .filter(candidate -> candidate.trigger() == PortOneCancellationTrigger.API)
            .toList();
        List<PortOneCancellation> bound = apiCancellations.stream()
            .filter(candidate -> candidate.id() != null && boundIds.contains(candidate.id()))
            .toList();
        List<PortOneCancellation> unbound = apiCancellations.stream()
            .filter(candidate -> !bound.contains(candidate))
            .toList();

        // 1. ID가 없으면 묶을 근거가 없고, 요청 시각이 없으면 이 환불 전후를 가를 수 없다
        if (unbound.stream().anyMatch(candidate -> !StringUtils.hasText(candidate.id())
            || candidate.requestedAt() == null)) {
            return Decision.manualReview("ID 또는 요청 시각이 없는 서버 API 취소가 있음");
        }
        // 2. 다른 환불의 취소가 아직 살아 있다. 그 결과가 정해지기 전에는 이 환불의 취소를 고르거나 새로 요청하지 않는다
        if (bound.stream().anyMatch(candidate -> candidate.status() != PortOneCancellationStatus.FAILED)) {
            return Decision.manualReview("다른 환불에 묶인 서버 API 취소가 진행 중이거나 성공함");
        }
        // 3. 이 환불보다 먼저 요청된 취소는 이 환불의 것이 아니다. 실패로 끝나지 않았으면 과거 취소의 결과가 DB에 반영되지 못한 것이다.
        // 서버와 PG 시계가 어긋나 이 환불의 취소가 과거로 보여도 여기서 멈출 뿐 잘못 묶거나 다시 취소하지 않는다
        LocalDateTime requestedAt = refund.getRequestedAt();
        if (unbound.stream().anyMatch(candidate -> candidate.requestedAt().isBefore(requestedAt)
            && candidate.status() != PortOneCancellationStatus.FAILED)) {
            return Decision.manualReview("이 환불보다 먼저 요청된 서버 API 취소가 진행 중이거나 성공함");
        }
        List<PortOneCancellation> current = unbound.stream()
            .filter(candidate -> !candidate.requestedAt().isBefore(requestedAt))
            .toList();
        // 4. 어느 것이 이 환불의 취소인지 고르지 않는다
        if (current.size() > 1) {
            return Decision.manualReview("이 환불 이후의 서버 API 취소가 " + current.size() + "건");
        }
        // 5. 이 환불 이후의 취소가 한 건뿐이다. POST 응답을 못 받은 그 취소다
        if (current.size() == 1) {
            return applyExisting(refund, portOnePayment, current.get(0));
        }
        // 6. 남은 API 취소는 실패로 끝난 과거 시도뿐이다
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
        MartRejectionPaymentResult applied =
            martRejectionProcessor.apply(refund.getPayment().getId(), refund.getId(), cancellation.toCancelResult());
        log.info("마트 거절 환불 기존 취소 반영: refundId={}, cancellationId={}, outcome={}",
            refund.getId(), cancellation.id(), applied.outcome());
        return decide(refund, cancellation.status() == PortOneCancellationStatus.SUCCEEDED, cancellation.id(), applied,
            "PortOne 취소 처리 중: cancellationId=" + cancellation.id());
    }

    // 이 환불의 취소가 아직 없다. PG에서 결제가 여전히 PAID일 때만 돌려준다 — 부분 취소·취소된 결제를 근거 없이 다시 취소하지 않는다
    private Decision requestCancel(Refund refund, PortOnePayment portOnePayment) {
        // 앞선 외부 취소 판정이 걸러 정상 흐름에서는 걸리지 않지만, 새 취소를 만드는 POST 직전의 마지막 방어선이라 남긴다
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
        MartRejectionPaymentResult applied =
            martRejectionProcessor.apply(refund.getPayment().getId(), refund.getId(), result);
        log.info("마트 거절 환불 취소 요청 반영: refundId={}, portOneOutcome={}, outcome={}",
            refund.getId(), result.outcome(), applied.outcome());
        return decide(refund, result.outcome() == PortOneCancelOutcome.SUCCEEDED, result.cancellationId(), applied,
            "취소 결과 미확정: portOneOutcome=" + result.outcome() + ", detail=" + result.detail());
    }

    // TX2 결과를 실행 판정으로 바꾼다. 값을 받는 switch 식이라 결과 종류가 늘면 컴파일이 실패한다
    private Decision decide(Refund refund, boolean pgSucceeded, String cancellationId,
                            MartRejectionPaymentResult applied, String pendingReason) {
        return switch (applied.outcome()) {
            case COMPLETED -> Decision.resolved(applied);
            case PENDING -> Decision.retry(pendingReason, applied);
            // 판정과 잠금 사이에 다른 경로가 이 환불을 실패로 끝냈을 수 있다. PortOne에서는 취소가 성공했으므로 정상 종료로 기록하지 않는다.
            // 반영 결과도 넘기지 않는다 — 거절 API가 그 실패를 보고 PG 성공을 409로 답하면 안 된다
            case PG_FAILED, REQUEST_REJECTED -> pgSucceeded
                ? Decision.manualReview("PortOne 취소는 성공했지만 마트 거절 환불이 FAILED 상태: refundId="
                                        + refund.getId() + ", cancellationId=" + cancellationId)
                : Decision.resolved(applied);
        };
    }

    // 환불 행은 REQUESTED·COMPLETED·FAILED만 갖는다. 재실행·수동 확인은 상태로 남기지 않고 예약과 로그로만 표현한다
    private AdminRefundExecution finish(Refund refund, Decision decision) {
        return switch (decision.outcome()) {
            case RESOLVED -> AdminRefundExecution.applied(RefundReconcileOutcome.RESOLVED, decision.applied());
            case MANUAL_REVIEW -> {
                log.error("마트 거절 환불 수동 확인 필요: refundId={}, paymentId={}, reason={}",
                    refund.getId(), refund.getPayment().getId(), decision.reason());
                yield AdminRefundExecution.notApplied(RefundReconcileOutcome.MANUAL_REVIEW);
            }
            case RESCHEDULED -> {
                LocalDateTime now = LocalDateTime.now(clock);
                if (!now.isBefore(refund.getRequestedAt().plus(RECHECK_WINDOW))) {
                    // 자동으로 실패 처리하지 않는다. 운영이 보도록 남기고 Fallback이 시간 단위로 다시 넣는다
                    log.error("마트 거절 환불이 {}시간 넘게 미확정: refundId={}, paymentId={}, reason={}",
                        RECHECK_WINDOW.toHours(), refund.getId(), refund.getPayment().getId(), decision.reason());
                    yield new AdminRefundExecution(RefundReconcileOutcome.STALLED, decision.applied());
                }
                scheduleRecheck(refund.getId(), decision.reason());
                yield new AdminRefundExecution(RefundReconcileOutcome.RESCHEDULED, decision.applied());
            }
            case SKIPPED, STALLED -> throw new IllegalStateException("실행 판정은 " + decision.outcome() + "가 될 수 없습니다.");
        };
    }

    // 같은 환불을 거절 API·Fallback도 등록하므로 더 이른 예약을 늦추지 않는 enqueueIfEarlier를 쓴다
    private void scheduleRecheck(Long refundId, String reason) {
        Instant executeAt = clock.instant().plus(RECHECK_DELAY);
        try {
            paymentDelayQueue.enqueueIfEarlier(PaymentDelayTask.adminRefundExecute(refundId), executeAt);
            log.info("마트 거절 환불 실행 예약: refundId={}, executeAt={}, reason={}", refundId, executeAt, reason);
        } catch (DataAccessException e) {
            // 환불은 REQUESTED 그대로라 Fallback이 다시 등록한다
            log.error("마트 거절 환불 실행 예약 실패: refundId={}, reason={}", refundId, reason, e);
        }
    }

    // 실행 판정과 로그용 사유. applied는 이번 실행에서 TX2가 돌았을 때만 있다.
    // 수동 확인 팩터리에는 applied 인자가 없다 — 운영 확인이 필요한 결과를 응답에 실을 수 없게 한다
    private record Decision(RefundReconcileOutcome outcome, String reason, MartRejectionPaymentResult applied) {

        static Decision resolved(MartRejectionPaymentResult applied) {
            return new Decision(RefundReconcileOutcome.RESOLVED, null, applied);
        }

        static Decision retry(String reason) {
            return new Decision(RefundReconcileOutcome.RESCHEDULED, reason, null);
        }

        static Decision retry(String reason, MartRejectionPaymentResult applied) {
            return new Decision(RefundReconcileOutcome.RESCHEDULED, reason, applied);
        }

        static Decision manualReview(String reason) {
            return new Decision(RefundReconcileOutcome.MANUAL_REVIEW, reason, null);
        }
    }
}
