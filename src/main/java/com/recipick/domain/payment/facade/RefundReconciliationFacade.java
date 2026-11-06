package com.recipick.domain.payment.facade;

import com.recipick.domain.payment.config.MemberRefundRecoveryProperties;
import com.recipick.domain.payment.dto.PaymentCancelResult;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
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
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// 결과가 확정되지 않은 회원 환불(REQUESTED)을 PortOne 재조회로 마무리한다. Worker가 큐에서 꺼낸 REFUND_RECONCILE:{id}마다 부른다.
// 이미 시작된 취소의 현재 상태를 다시 묻는 것이 기본이고, 조회로 이 환불의 취소가 없음을 확인했을 때만 같은 서버 멱등 키로
// 취소 POST를 다시 보낸다(스위치·멱등 보장 시간 안·안전 조건 충족). 취소 API의 TX1 뒤 POST가 나가지 못한 환불을 그대로 두지 않기 위해서다.
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

    // 자동 POST는 멱등 보장 시간이 끝나기 이만큼 전까지만 한다. 경계에서 보내면 PortOne 쪽 시계·전송 지연으로 보장 밖에 닿아
    // 같은 키가 새 취소로 처리될 수 있다. 그 뒤는 조회만 하고 운영 확인으로 넘긴다
    static final Duration AUTO_REPOST_MARGIN = Duration.ofMinutes(5);

    // 취소 API·SYSTEM·마트 거절 환불과 같은 규칙. 환불 행 하나가 PortOne 취소 작업 하나다
    private static final String PORTONE_IDEMPOTENCY_KEY_PREFIX = "recipick-refund-";

    // 상품 결제가 받는 유일한 통화. 결제 확정 판정과 같은 기준이다
    private static final String CURRENCY_KRW = "KRW";

    private final PaymentService paymentService;
    private final PortOnePaymentClient portOnePaymentClient;
    private final PaymentCancelProcessor paymentCancelProcessor;
    private final PaymentDelayQueue paymentDelayQueue;
    private final Clock clock;
    private final MemberRefundRecoveryProperties recoveryProperties;

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
        // 취소 API의 TX2가 반영하지 못하고 남긴 PortOne 거절이다. 거절 응답에는 취소 내역이 없어 조회로는 확인할 수 없으므로
        // PortOne을 다시 부르지 않고 그 기록으로 반영한다
        if (refund.hasPendingRejection()) {
            return applyRecordedRejection(refund);
        }

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

    // 취소 API가 받았던 거절과 같은 결과로 TX2를 다시 돌린다. 잠금 실패는 바깥 catch가 재예약한다
    private Decision applyRecordedRejection(Refund refund) {
        PortOneCancelResult rejected =
            new PortOneCancelResult(PortOneCancelOutcome.REJECTED, null, null, refund.getFailureReason());
        PaymentCancelResult applied =
            paymentCancelProcessor.apply(refund.getPayment().getId(), refund.getId(), rejected);
        log.info("기록한 PortOne 거절 반영: refundId={}, outcome={}", refund.getId(), applied.outcome());
        // 판정과 잠금 사이에 다른 경로가 이 환불을 다른 결과로 끝냈다. 거절 기록과 맞지 않으므로 운영에 넘긴다
        if (applied.outcome() != PaymentCancelOutcome.REQUEST_REJECTED) {
            return Decision.manualReview("기록한 PortOne 거절과 반영 결과가 다름: refundId=" + refund.getId()
                + ", outcome=" + applied.outcome());
        }
        return Decision.resolved();
    }

    // 자동 정합화할 외부 취소가 없다. 서버가 API로 요청한 취소를 이 환불에 묶는 판정이다
    private Decision reconcileApiCancellation(Refund refund, PortOnePayment portOnePayment, String cancellationId) {
        // 취소 API·웹훅이 받은 ID가 있으면 그 취소만 본다. 다른 취소로 갈아타지 않는다
        if (cancellationId != null) {
            Optional<PortOneCancellation> byId = portOnePayment.cancellations().stream()
                .filter(candidate -> cancellationId.equals(candidate.id()))
                .findFirst();
            if (byId.isEmpty()) {
                // ID는 받았는데 조회 결과에 아직 없다. PortOne 반영이 늦는 경우다
                return Decision.retry("조회 결과에 취소 내역 없음: cancellationId=" + cancellationId);
            }
            return applyCancellation(refund, portOnePayment, byId.get());
        }
        // 취소 API가 응답을 못 받아 ID가 없다. 서버 API 취소 중 이 환불의 것을 고르는 판정은 마트 거절 실행기·취소 웹훅과 같은 규칙이다.
        // 금액이 다른 API 취소도 고른다 — 걸러 버리면 "취소 없음"으로 보여 새 POST가 나간다. 금액은 반영하는 쪽이 막는다
        Set<String> boundIds = paymentService.findBoundCancellationIds(refund.getPayment().getId(), refund.getId());
        ApiCancellationSelection selection = RefundCancellationRules.selectCurrentApiCancellation(
            portOnePayment, boundIds, refund.getRequestedAt());
        return switch (selection.type()) {
            case SELECTED -> applyCancellation(refund, portOnePayment, selection.cancellation());
            case AMBIGUOUS -> Decision.manualReview(selection.reason());
            case NONE -> requestMissingCancellation(refund, portOnePayment);
        };
    }

    // 이 환불의 취소가 PortOne에 없다. 취소 API의 TX1은 커밋됐는데 POST가 나가지 못한 경우(POST 직전 서버 중단·연결 실패)다.
    // 조회에 안 보인다는 것이 POST가 닿지 않았다는 증명은 아니므로, 같은 서버 키로 PortOne 멱등 보장 시간 안에서만 다시 보낸다 —
    // 이미 받았다면 PortOne이 같은 응답을 돌려주고, 처리 중이면 409로 거절해 이중 취소가 되지 않는다.
    // 반영은 취소 API의 TX2(apply)를 그대로 쓴다. 회원의 같은 키 재요청과 겹쳐도 키가 같고 끝난 환불은 apply가 바꾸지 않는다
    private Decision requestMissingCancellation(Refund refund, PortOnePayment portOnePayment) {
        if (!recoveryProperties.autoRepostEnabled()) {
            // 기존 동작. PortOne에 아직 안 보이거나 요청이 닿지 않았다. 다시 보내지 않고 계속 지켜본다
            return Decision.retry("서버가 요청한 전액 취소 내역이 아직 없음 — 자동 POST 꺼짐");
        }
        Payment payment = refund.getPayment();
        // 자동 POST는 회원 카드 결제의 취소 접수 상태에서만 한다. 0원 결제·다른 상태는 이 경로에 올 수 없는 데이터라 운영에 넘긴다
        if (payment.getPaymentMethod() != PaymentMethod.CARD
            || payment.getStatus() != PaymentStatus.CANCEL_REQUESTED) {
            return Decision.manualReview("회원 카드 취소 접수 상태가 아님: paymentMethod=" + payment.getPaymentMethod()
                + ", paymentStatus=" + payment.getStatus());
        }
        // 앞선 외부 취소 판정이 걸러 정상 흐름에서는 걸리지 않지만, 새 취소를 만드는 POST 직전의 마지막 방어선이라 남긴다
        if (RefundCancellationRules.hasActiveNonApiCancellation(portOnePayment)) {
            return Decision.manualReview("API가 아닌 취소가 진행 중이거나 완료됨");
        }
        // PG에서 결제가 여전히 PAID일 때만 돌려준다 — 부분 취소·취소된 결제를 근거 없이 다시 취소하지 않는다
        if (portOnePayment.status() != PortOnePaymentStatus.PAID) {
            return Decision.manualReview("PG 결제가 PAID가 아닌데 이 환불의 취소 내역이 없음: status="
                + portOnePayment.status());
        }
        // 조회한 결제가 이 결제·이 주문인지. 다른 결제를 취소하면 되돌릴 수 없다. 양쪽이 비어 있으면 같다고 보지 않는다
        if (!StringUtils.hasText(portOnePayment.id()) || !StringUtils.hasText(payment.getPortonePaymentId())
            || portOnePayment.customDataOrderId() == null || payment.getOrderId() == null
            || !portOnePayment.id().equals(payment.getPortonePaymentId())
            || !portOnePayment.customDataOrderId().equals(payment.getOrderId())) {
            return Decision.manualReview("PG 결제 ID 또는 주문 ID 불일치: pgPaymentId=" + portOnePayment.id()
                + ", pgOrderId=" + portOnePayment.customDataOrderId());
        }
        // 통화·원수납액·누적 취소액이 응답에 없으면 "아직 취소된 돈이 없다"를 확인할 근거가 없다. 모순으로 단정하지 않고 다시 묻는다
        if (!StringUtils.hasText(portOnePayment.currency()) || portOnePayment.totalAmount() == null
            || portOnePayment.cancelledAmount() == null) {
            return Decision.retry("PG 통화·원수납액·누적 취소액 없음 — 취소 여부를 확인할 수 없음");
        }
        // 회원 환불은 전액 취소라 세 값이 환불 금액과 정확히 맞아야 한다
        if (!CURRENCY_KRW.equals(portOnePayment.currency())
            || !Objects.equals(portOnePayment.totalAmount(), refund.getAmount())
            || !Objects.equals(payment.getAmount(), refund.getAmount())
            || portOnePayment.cancelledAmount() != 0L) {
            return Decision.manualReview("PG 통화·원수납액·누적 취소액이 환불 금액과 맞지 않음: currency="
                + portOnePayment.currency() + ", pgTotal=" + portOnePayment.totalAmount()
                + ", pgCancelled=" + portOnePayment.cancelledAmount() + ", refundAmount=" + refund.getAmount());
        }
        if (refund.getRequestedAt() == null) {
            // 시간 창을 잴 기준이 없다. 보내지 않고 운영에 넘긴다
            return Decision.manualReview("환불 최초 접수 시각 없음");
        }
        if (!withinAutoRepostWindow(refund)) {
            // 멱등 보장이 끝나 가면 같은 키도 새 취소가 될 수 있다. 조회만 계속하고 3시간 뒤 STALLED로 운영에 넘어간다
            return Decision.retry("자동 POST 시간 창 종료 — 조회만 계속");
        }

        // POST 직전 재조회. 조회 결과를 기다리는 사이 회원 재요청·웹훅이 이 환불을 끝냈거나 취소 ID를 남겼을 수 있다.
        // 잠금 없이 읽는다 — PG 호출 중 행 잠금을 붙잡지 않기 위해서다. 남는 틈은 같은 멱등 키와 apply의 끝난 환불 판정이 막는다
        Optional<Refund> latest = paymentService.findRefundWithPayment(refund.getId());
        if (latest.isEmpty()) {
            return Decision.manualReview("POST 직전 환불 행이 없음");
        }
        Refund current = latest.get();
        if (current.getStatus() != RefundStatus.REQUESTED) {
            // 다른 경로가 끝냈다. 이번 실행이 바꾼 것은 없지만 더 볼 것도 없다
            return Decision.resolved();
        }
        if (current.hasPendingRejection() || current.getPortoneCancellationId() != null) {
            return Decision.retry("POST 직전에 거절 기록 또는 취소 ID가 생김 — 다음 재확인이 그 기록으로 판정");
        }
        // 재조회한 환불·결제가 조회를 시작할 때 본 것과 같은 회원 카드 취소·같은 PG 결제·같은 주문·같은 금액인지 다시 본다
        Payment currentPayment = current.getPayment();
        if (current.getRequestedBy() != RefundRequester.MEMBER
            || currentPayment.getPaymentMethod() != PaymentMethod.CARD
            || currentPayment.getStatus() != PaymentStatus.CANCEL_REQUESTED
            || !Objects.equals(currentPayment.getPortonePaymentId(), portOnePayment.id())
            || !Objects.equals(currentPayment.getOrderId(), portOnePayment.customDataOrderId())
            || !Objects.equals(current.getAmount(), refund.getAmount())
            || !Objects.equals(currentPayment.getAmount(), current.getAmount())
            || !withinAutoRepostWindow(current)) {
            return Decision.manualReview("POST 직전 환불·결제가 재확인 시작 때와 다름: paymentStatus="
                + currentPayment.getStatus() + ", pgPaymentId=" + currentPayment.getPortonePaymentId());
        }

        PortOneCancelResult result = portOnePaymentClient.cancelPayment(
            currentPayment.getPortonePaymentId(),
            PORTONE_IDEMPOTENCY_KEY_PREFIX + current.getId(),
            current.getReason(),
            current.getAmount());
        PaymentCancelResult applied = paymentCancelProcessor.apply(currentPayment.getId(), current.getId(), result);
        log.info("회원 환불 취소 재요청 반영: refundId={}, portOneOutcome={}, outcome={}",
            current.getId(), result.outcome(), applied.outcome());
        if (result.outcome() == PortOneCancelOutcome.SUCCEEDED
            && (applied.outcome() == PaymentCancelOutcome.PG_FAILED
            || applied.outcome() == PaymentCancelOutcome.REQUEST_REJECTED)) {
            // 판정과 잠금 사이에 다른 경로가 이 환불을 실패로 끝냈다. PortOne에서는 취소가 성공했으므로 정상 종료로 기록하지 않는다
            return Decision.manualReview("PortOne 취소는 성공했지만 회원 환불이 실패 상태로 끝남: refundId="
                + current.getId() + ", cancellationId=" + result.cancellationId() + ", outcome=" + applied.outcome());
        }
        return switch (applied.outcome()) {
            case CANCELED, PG_FAILED, REQUEST_REJECTED -> Decision.resolved();
            // 취소 ID를 받았으면 다음 재확인이 ID로 찾고, 결과 불명이면 다시 조회한다
            case PENDING -> Decision.retry("취소 재요청 결과 미확정: portOneOutcome=" + result.outcome()
                + ", detail=" + result.detail());
            case CANCELED_BEFORE_PAY -> throw new IllegalStateException(
                "결제 후 취소 반영에서 나올 수 없는 결과: " + applied.outcome());
        };
    }

    // 최초 환불 접수부터 멱등 보장 시간(3시간)에서 여유(5분)를 뺀 시각 전까지만 자동 POST한다.
    // 접수 시각이 없거나 미래면(시계 어긋남·잘못된 데이터) 창 안으로 보지 않는다
    private boolean withinAutoRepostWindow(Refund refund) {
        LocalDateTime requestedAt = refund.getRequestedAt();
        if (requestedAt == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime deadline = requestedAt.plus(RECHECK_WINDOW).minus(AUTO_REPOST_MARGIN);
        return !requestedAt.isAfter(now) && now.isBefore(deadline);
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
