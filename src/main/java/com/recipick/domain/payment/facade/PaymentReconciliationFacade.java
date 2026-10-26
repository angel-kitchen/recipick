package com.recipick.domain.payment.facade;

import com.recipick.domain.payment.dto.PaymentCancelResult;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.WebhookEvent;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.service.PaymentApprovalOutcome;
import com.recipick.domain.payment.service.PaymentDelayQueue;
import com.recipick.domain.payment.service.PaymentDelayTask;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.domain.payment.service.WebhookEventService;
import com.recipick.domain.payment.service.WebhookProcessOutcome;
import com.recipick.global.exception.BusinessException;
import com.recipick.infra.portone.PortOnePaymentClient;
import com.recipick.infra.portone.PortOneWebhookParser;
import com.recipick.infra.portone.dto.PortOneCancelOutcome;
import com.recipick.infra.portone.dto.PortOneCancelResult;
import com.recipick.infra.portone.dto.PortOneCancellation;
import com.recipick.infra.portone.dto.PortOneCancellationStatus;
import com.recipick.infra.portone.dto.PortOneCancellationTrigger;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOneWebhook;
import com.recipick.infra.portone.dto.PortOneWebhookType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// 웹훅 inbox 한 건을 PortOne 재조회 결과로 결제·환불에 반영한다. Worker가 큐에서 꺼낸 WEBHOOK_PROCESS:{id}마다 부른다.
// 트랜잭션을 걸지 않는다 — TX-A(claim) → PortOne 조회(락 없음) → TX-B(기존 Processor) → TX-C(결과 기록) 순이다.
// 웹훅 본문의 값은 믿지 않는다. 어느 결제·환불을 볼지 정하는 데만 쓰고 상태 판정은 PortOne 재조회 결과로만 한다.
// TX-C는 claim 때 받은 attemptCount(처리권 번호)가 그대로일 때만 기록한다. 멈췄다 돌아온 Worker가 다시 잡은 Worker의
// 결과를 덮어쓰지 않게 하기 위해서다. TX-B는 두 Worker가 모두 돌 수 있지만 Processor가 PAYMENT 잠금 아래에서 끝난 상태를
// 다시 바꾸지 않으므로 반영은 한 번이다.
// TX-C 자체가 실패하면 행은 PROCESSING으로 남고, PROCESSING_TIMEOUT 뒤 Fallback Sweep이 다시 잡는다
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciliationFacade {

    // PROCESSING인 채 이 시간이 지나면 Worker가 죽은 것으로 보고 다시 잡는다. PortOne 조회 timeout(초 단위)보다 훨씬 길다
    static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(10);

    // 이 횟수까지 잡아 보고도 끝내지 못하면 MANUAL_REVIEW. 같은 웹훅을 무한히 다시 하지 않는다
    static final int MAX_ATTEMPTS = 5;

    // 재시도 간격 1분 → 2분 → 4분 → 8분. PortOne 일시 장애가 15분 안에 걷히면 자동으로 따라잡는다
    static final Duration RETRY_BASE_DELAY = Duration.ofMinutes(1);

    private final WebhookEventService webhookEventService;
    private final PaymentService paymentService;
    private final PortOnePaymentClient portOnePaymentClient;
    private final PortOneWebhookParser portOneWebhookParser;
    private final PaymentConfirmProcessor paymentConfirmProcessor;
    private final PaymentCancelProcessor paymentCancelProcessor;
    private final PaymentDelayQueue paymentDelayQueue;
    private final Clock clock;

    public WebhookProcessOutcome processWebhook(Long webhookEventId) {
        // TX-A. 잠금 아래에서 상태를 다시 보고 PROCESSING으로 바꾼 뒤 커밋한다 — PortOne을 기다리는 동안 행을 잠그지 않는다
        Optional<WebhookEvent> claimed =
            webhookEventService.claim(webhookEventId, LocalDateTime.now(clock), PROCESSING_TIMEOUT);
        if (claimed.isEmpty()) {
            // 이미 끝났거나 다른 Worker가 처리 중이거나 없는 행. 큐에 두 번 들어와도 반영은 한 번이다
            log.info("웹훅 이벤트를 잡지 못해 건너뜀: webhookEventId={}", webhookEventId);
            return WebhookProcessOutcome.SKIPPED;
        }
        WebhookEvent event = claimed.get();

        Decision decision;
        try {
            decision = reconcile(event);
        } catch (TransientDataAccessException e) {
            // 락 대기 초과·쿼리 timeout처럼 다시 하면 풀릴 수 있는 DB 장애. backoff 뒤 다시 한다
            log.warn("일시적 DB 장애로 웹훅 재시도: webhookEventId={}, type={}", event.getId(), event.getType(), e);
            decision = Decision.retry("일시적 DB 장애: " + e.getClass().getSimpleName());
        } catch (RuntimeException e) {
            // 코드 버그·예상 못한 상태. 다시 해도 같은 결과라 재시도하지 않고 바로 운영에 넘긴다
            log.error("웹훅 정합화 내부 오류: webhookEventId={}, type={}", event.getId(), event.getType(), e);
            decision = Decision.manualReview("예상하지 못한 내부 오류: " + e.getClass().getSimpleName());
        }
        return record(event, decision);
    }

    private Decision reconcile(WebhookEvent event) {
        Optional<PortOneWebhookType> type = PortOneWebhookType.of(event.getType());
        if (type.isEmpty()) {
            return Decision.ignored("처리하지 않는 type: " + event.getType());
        }
        // 우리 결제가 아니면 PortOne을 묻지 않는다. 다른 환경·상점의 결제 ID가 섞여 들어온 경우다.
        // 멤버십 결제 웹훅도 지금은 여기서 IGNORED로 끝나고, 종료 상태라 나중에 다시 처리되지 않는다 — 멤버십 결제를
        // 구현할 때 이 조회 앞에서 일반 결제·멤버십 결제를 나눠 보내야 한다
        Optional<Payment> payment = paymentService.findByPortonePaymentId(event.getPortonePaymentId());
        if (payment.isEmpty()) {
            return Decision.ignored("우리 결제가 아님: portonePaymentId=" + event.getPortonePaymentId());
        }
        if (type.get() == PortOneWebhookType.PARTIAL_CANCELED) {
            // 전액 취소만 허용하는 정책이라 부분 취소는 자동 반영 규칙이 없다. PortOne 조회 없이 바로 운영에 넘긴다
            return Decision.manualReview("부분 취소는 자동 반영하지 않음");
        }

        // 락 없이 PortOne을 묻는다. 웹훅 본문의 상태가 아니라 이 결과로만 판정한다
        PortOnePayment portOnePayment;
        try {
            portOnePayment = portOnePaymentClient.getPayment(event.getPortonePaymentId());
        } catch (BusinessException e) {
            // 404(아직 없음)·5xx·timeout 모두 일시적일 수 있다. 클라이언트가 WARN을 남겼으므로 여기서는 사유만 적는다
            return Decision.retry("PortOne 조회 실패: " + e.getErrorCode().getCode());
        }

        if (type.get() == PortOneWebhookType.PAID || type.get() == PortOneWebhookType.FAILED) {
            return confirm(payment.get(), portOnePayment);
        }
        return reconcileCancel(event, payment.get(), portOnePayment);
    }

    // TX-B. 결제 확정 API와 같은 Processor를 쓴다 — 웹훅이 먼저 오든 API가 먼저 오든 반영은 한 번이다
    private Decision confirm(Payment payment, PortOnePayment portOnePayment) {
        PaymentApprovalOutcome outcome = paymentConfirmProcessor.process(payment.getOrderId(), portOnePayment);
        log.info("웹훅 결제 확정 판정: orderId={}, outcome={}", payment.getOrderId(), outcome);
        return switch (outcome) {
            // 지금 끝냈거나 이미 끝난 결제. 실패 판정(PG_FAILED·만료·금액 불일치)도 Processor가 반영을 마쳤으니 웹훅 몫은 끝이다
            case APPROVED, ALREADY_PAID, PG_FAILED, AMOUNT_MISMATCH, EXPIRED, ALREADY_CLOSED, INVALID_STATUS ->
                Decision.processed();
            // Paid 웹훅인데 PortOne이 아직 PAID가 아니거나 금액·시각이 비어 있다. 반영이 늦는 경우라 잠시 뒤 다시 묻는다
            case NOT_PAID -> Decision.retry("PortOne 결제 미확정: status=" + portOnePayment.status());
            // customData의 주문 ID가 우리 결제의 주문과 다르다. 자동으로 고칠 수 없는 불일치
            case ORDER_MISMATCH -> Decision.manualReview(
                "customData 주문 ID 불일치: " + portOnePayment.customDataOrderId());
        };
    }

    // 취소 웹훅. 환불 행을 찾고 PortOne 조회의 취소 내역 하나를 그 환불에 묶은 뒤, 취소 API의 TX2(apply)에 그대로 넘긴다
    private Decision reconcileCancel(WebhookEvent event, Payment payment, PortOnePayment portOnePayment) {
        String webhookCancellationId = cancellationIdOf(event);

        // ① 웹훅의 취소 ID로 환불을 찾는다. 취소 API의 TX2가 먼저 끝났으면 여기서 찾힌다
        Refund refund;
        Optional<Refund> byCancellationId = StringUtils.hasText(webhookCancellationId)
            ? paymentService.findRefundByPortoneCancellationId(webhookCancellationId)
            : Optional.empty();
        if (byCancellationId.isPresent()) {
            refund = byCancellationId.get();
        } else {
            // ② 취소 ID를 아직 못 받은 환불(TX2 전에 웹훅이 먼저 온 경우). 정확히 한 건일 때만 그 환불로 본다
            List<Refund> requested = paymentService.findRequestedMemberRefunds(payment.getId());
            if (requested.isEmpty()) {
                return Decision.manualReview("대응하는 회원 환불 없음 — 콘솔 등 외부 취소 가능성");
            }
            if (requested.size() > 1) {
                return Decision.manualReview("REQUESTED 회원 환불이 여러 건: " + requested.size());
            }
            refund = requested.get(0);
        }

        // apply는 회원 환불·같은 결제만 받는다(invariant). 아니면 넘기지 않고 운영에 맡긴다 — SYSTEM 환불 정합화는 아직 없다
        if (refund.getRequestedBy() != RefundRequester.MEMBER) {
            return Decision.manualReview(
                "회원 환불이 아님: refundId=" + refund.getId() + ", requestedBy=" + refund.getRequestedBy());
        }
        if (!refund.getPayment().getId().equals(payment.getId())) {
            return Decision.manualReview("취소 ID의 환불이 다른 결제: refundId=" + refund.getId());
        }
        String refundCancellationId = refund.getPortoneCancellationId();
        if (refundCancellationId != null && StringUtils.hasText(webhookCancellationId)
            && !refundCancellationId.equals(webhookCancellationId)) {
            // 환불은 취소 X로 기록돼 있는데 웹훅은 취소 Y를 말한다. 같은 결제에 취소가 둘인 상황이라 자동으로 고르지 않는다
            return Decision.manualReview("웹훅 취소 ID가 환불의 취소 ID와 다름: " + webhookCancellationId);
        }

        // 조회 결과에서 이 환불의 취소 내역을 고른다. 환불에 기록된 ID가 우선, 없으면 웹훅의 ID
        String cancellationId = refundCancellationId != null ? refundCancellationId : webhookCancellationId;
        PortOneCancellation cancellation;
        if (StringUtils.hasText(cancellationId)) {
            Optional<PortOneCancellation> found = portOnePayment.cancellations().stream()
                .filter(candidate -> cancellationId.equals(candidate.id()))
                .findFirst();
            if (found.isEmpty()) {
                // 웹훅·환불이 가리키는 취소가 조회 결과에 아직 없다. PortOne 반영이 늦는 경우라 잠시 뒤 다시 묻는다
                return Decision.retry("조회 결과에 취소 내역 없음: cancellationId=" + cancellationId);
            }
            cancellation = found.get();
            // 환불에 아직 취소 ID가 없으면 웹훅의 취소가 정말 우리 요청(API·전액)인지 확인한 뒤에만 묶는다
            if (refundCancellationId == null && !isOurs(cancellation, refund)) {
                return Decision.manualReview("환불과 금액·요청 경로가 다른 취소 내역: cancellationId=" + cancellationId);
            }
        } else {
            // 취소 ID가 어디에도 없다. API로 요청한 우리 금액의 취소가 정확히 한 건일 때만 그것으로 본다
            List<PortOneCancellation> ours = portOnePayment.cancellations().stream()
                .filter(candidate -> isOurs(candidate, refund))
                .toList();
            if (ours.size() != 1) {
                return Decision.manualReview("우리 금액의 API 취소 내역이 " + ours.size() + "건");
            }
            cancellation = ours.get(0);
        }

        // 전액 취소만 허용한다. 어느 경로로 골랐든 금액이 환불과 다르면 이 환불의 취소로 볼 수 없다
        if (!Objects.equals(cancellation.totalAmount(), refund.getAmount())) {
            return Decision.manualReview("취소 금액 불일치: refundAmount=" + refund.getAmount()
                + ", cancellationAmount=" + cancellation.totalAmount());
        }
        // 고른 취소 말고 성공한 취소가 또 있으면 실제 결제는 이미 취소됐을 수 있다. 예: API 취소는 FAILED인데
        // 콘솔 취소가 SUCCEEDED면, 고른 것만 반영해 PAID로 되돌리는 순간 PortOne의 CANCELED와 어긋난다
        if (hasOtherSucceededCancellation(portOnePayment, cancellation)) {
            return Decision.manualReview("고른 취소 외에 성공한 취소가 또 있음: selected=" + cancellation.id());
        }

        // TX-B. 취소 API의 TX2를 그대로 쓴다. 끝난 환불은 apply가 바꾸지 않고 그 결과를 돌려주므로 웹훅이 TX2 뒤에 와도 안전하다
        PortOneCancelResult result = cancellation.toCancelResult();
        PaymentCancelResult applied = paymentCancelProcessor.apply(payment.getId(), refund.getId(), result);
        log.info("웹훅 취소 반영: paymentId={}, refundId={}, cancellationId={}, outcome={}",
            payment.getId(), refund.getId(), cancellation.id(), applied.outcome());
        return switch (applied.outcome()) {
            // 완료했거나(또는 이미 완료), PortOne이 FAILED를 명시해 PAID로 되돌렸다. 어느 쪽이든 반영은 끝났다
            case CANCELED, PG_FAILED, REQUEST_REJECTED -> Decision.processed();
            // 변환 결과가 REQUESTED면 apply가 취소 ID를 기록했으므로 이 웹훅의 몫은 끝이다 — 같은 웹훅을 다시 돌리지 않는다.
            // 완료 확인은 뒤에 올 Cancelled 웹훅이나 환불 재조회(6단계)가 맡는다. 원문 status가 아니라 변환 결과로 본다 —
            // ID 없는 REQUESTED는 toCancelResult가 UNCONFIRMED로 바꾸고 ID도 기록되지 않는다. 그 밖의 PENDING은 확정 근거가 없다
            case PENDING -> result.outcome() == PortOneCancelOutcome.REQUESTED
                ? Decision.processed()
                : Decision.manualReview("취소 결과 확정 불가: result=" + result.outcome()
                                        + ", status=" + cancellation.status() + ", cancellationId=" + cancellation.id()
                                        + ", canceledAt=" + cancellation.canceledAt());
            case CANCELED_BEFORE_PAY -> throw new IllegalStateException(
                "결제 후 취소 반영에서 나올 수 없는 결과: " + applied.outcome());
        };
    }

    // 서버가 API로 요청한 전액 취소인지. 콘솔·관리자·차지백 취소는 환불 행과 묶지 않는다
    private static boolean isOurs(PortOneCancellation cancellation, Refund refund) {
        return cancellation.trigger() == PortOneCancellationTrigger.API
            && Objects.equals(cancellation.totalAmount(), refund.getAmount());
    }

    // 같은 목록에서 꺼낸 객체라 참조로 비교한다. ID로 비교하면 ID가 비어 있는 취소끼리 같은 것으로 보인다
    private static boolean hasOtherSucceededCancellation(PortOnePayment portOnePayment, PortOneCancellation selected) {
        return portOnePayment.cancellations().stream()
            .anyMatch(candidate -> candidate != selected && candidate.status() == PortOneCancellationStatus.SUCCEEDED);
    }

    // 저장된 건 원문뿐이라 취소 ID는 본문을 다시 읽어 꺼낸다. 수신 때 파싱된 본문이므로 못 읽으면 ID 없는 웹훅으로 본다
    private String cancellationIdOf(WebhookEvent event) {
        return portOneWebhookParser.parse(event.getPayload())
            .map(PortOneWebhook::data)
            .map(PortOneWebhook.Data::cancellationId)
            .orElse(null);
    }

    // TX-C. 결과를 WEBHOOK_EVENT에 남긴다. 재시도는 DB에 먼저 적고 큐에 예약한다 — 큐 예약이 실패해도 DB가 원본이라 복구된다
    private WebhookProcessOutcome record(WebhookEvent event, Decision decision) {
        Decision finalDecision = decision.outcome() == WebhookProcessOutcome.RETRY_PENDING
            && event.getAttemptCount() >= MAX_ATTEMPTS
            ? Decision.manualReview("최대 시도 " + MAX_ATTEMPTS + "회 도달: " + decision.reason())
            : decision;
        Long id = event.getId();
        int attempt = event.getAttemptCount();
        String reason = finalDecision.reason();
        LocalDateTime now = LocalDateTime.now(clock);
        boolean recorded = switch (finalDecision.outcome()) {
            case PROCESSED -> webhookEventService.markProcessed(id, attempt, now);
            case IGNORED -> webhookEventService.markIgnored(id, attempt, reason, now);
            case MANUAL_REVIEW -> webhookEventService.markManualReview(id, attempt, reason, now);
            case RETRY_PENDING -> webhookEventService.markRetryPending(id, attempt, reason);
            case SKIPPED -> throw new IllegalStateException("정합화 판정은 SKIPPED가 될 수 없습니다.");
        };
        if (!recorded) {
            // 처리권이 다른 Worker로 넘어갔다. 그쪽이 결과를 기록하고 필요하면 재예약하므로 여기서는 아무것도 남기지 않는다
            log.warn("처리권이 넘어가 웹훅 결과를 기록하지 않음: webhookEventId={}, attempt={}, outcome={}",
                id, attempt, finalDecision.outcome());
            return WebhookProcessOutcome.SKIPPED;
        }

        switch (finalDecision.outcome()) {
            case IGNORED -> log.warn("웹훅 무시: webhookEventId={}, type={}, reason={}", id, event.getType(), reason);
            // 운영이 봐야 끝나는 건이라 ERROR로 남긴다
            case MANUAL_REVIEW -> log.error("웹훅 수동 확인 필요: webhookEventId={}, type={}, portonePaymentId={}, reason={}",
                id, event.getType(), event.getPortonePaymentId(), reason);
            case RETRY_PENDING -> scheduleRetry(event, reason);
            default -> { }
        }
        return finalDecision.outcome();
    }

    private void scheduleRetry(WebhookEvent event, String reason) {
        // attemptCount는 claim에서 1 이상이 됐고 MAX_ATTEMPTS 미만이라 1 << (n-1)은 최대 8이다
        Duration delay = RETRY_BASE_DELAY.multipliedBy(1L << (event.getAttemptCount() - 1));
        Instant executeAt = clock.instant().plus(delay);
        try {
            paymentDelayQueue.reschedule(PaymentDelayTask.webhookProcess(event.getId()), executeAt);
            log.warn("웹훅 재시도 예약: webhookEventId={}, attempt={}, executeAt={}, reason={}",
                event.getId(), event.getAttemptCount(), executeAt, reason);
        } catch (DataAccessException e) {
            // RETRY_PENDING은 이미 커밋됐다. 큐 예약만 놓친 것이라 Fallback Sweep(6단계)이 다시 등록한다
            log.error("웹훅 재시도 예약 실패 — RETRY_PENDING으로 남김: webhookEventId={}", event.getId(), e);
        }
    }

    // 정합화 판정과 기록용 사유. TX-C가 이 값대로 WEBHOOK_EVENT에 적는다
    private record Decision(WebhookProcessOutcome outcome, String reason) {

        static Decision processed() {
            return new Decision(WebhookProcessOutcome.PROCESSED, null);
        }

        static Decision ignored(String reason) {
            return new Decision(WebhookProcessOutcome.IGNORED, reason);
        }

        static Decision retry(String reason) {
            return new Decision(WebhookProcessOutcome.RETRY_PENDING, reason);
        }

        static Decision manualReview(String reason) {
            return new Decision(WebhookProcessOutcome.MANUAL_REVIEW, reason);
        }
    }
}
