package com.recipick.domain.payment.facade;

import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.service.PaymentApprovalOutcome;
import com.recipick.domain.payment.service.PaymentDelayQueue;
import com.recipick.domain.payment.service.PaymentDelayTask;
import com.recipick.domain.payment.service.PaymentExpirationOutcome;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.global.exception.ErrorCode;
import com.recipick.infra.portone.PortOnePaymentClient;
import com.recipick.infra.portone.dto.PortOnePayment;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

// 5분이 지난 결제를 PortOne에 다시 물어 어느 집행 경로로 보낼지 정한다. Worker가 큐에서 꺼낸 PAYMENT_EXPIRE:{id}마다 부른다.
// 트랜잭션을 걸지 않는다 — 스냅샷 조회(짧은 readOnly) → PortOne 조회(락 없음) → Processor(PAYMENT 잠금) 순이다.
// 상태를 직접 바꾸지 않는다. 미결제는 만료 Processor, PG가 PAID·FAILED를 보이면 확정 API·웹훅과 같은 확정 Processor가 맡아
// 세 경로가 같은 잠금과 같은 판정으로 수렴한다
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpirationFacade {

    // 아직 판단할 수 없을 때 다음 조회까지
    static final Duration RECHECK_DELAY = Duration.ofMinutes(1);

    // 만료 시각부터 이 시간 동안만 1분 간격으로 다시 본다. 지나면 Fallback 주기에 맡긴다.
    // Fallback도 같은 시간이 지난 IN_PROGRESS 결제를 복구 대상으로 고르므로 공개한다
    public static final Duration RECHECK_WINDOW = Duration.ofMinutes(10);

    private final PaymentService paymentService;
    private final PortOnePaymentClient portOnePaymentClient;
    private final PaymentConfirmProcessor paymentConfirmProcessor;
    private final PaymentExpirationProcessor paymentExpirationProcessor;
    private final SystemRefundExecutionFacade systemRefundExecutionFacade;
    private final PaymentDelayQueue paymentDelayQueue;
    private final Clock clock;

    public PaymentExpirationOutcome execute(Long paymentId) {
        // 스냅샷
        Optional<Payment> found;
        try {
            found = paymentService.findById(paymentId);
        } catch (TransientDataAccessException e) {
            // 결제를 못 읽어 재조회 시간 경계를 알 수 없다. 경계 없이 한 번 미루고, 이후 실행에서 경계를 다시 본다
            log.warn("결제 만료 스냅샷 조회 중 일시적 DB 장애: paymentId={}", paymentId, e);
            reschedule(paymentId, clock.instant().plus(RECHECK_DELAY), "스냅샷 조회 일시 장애");
            return PaymentExpirationOutcome.RESCHEDULED;
        } catch (RuntimeException e) {
            log.error("결제 만료 스냅샷 조회 중 내부 오류: paymentId={}", paymentId, e);
            return PaymentExpirationOutcome.MANUAL_REVIEW;
        }
        // 레시픽 DB에 결제가 없는 경우다. PortOne에 결제가 없는 404와 섞지 않는다
        if (found.isEmpty()) {
            log.warn("만료할 결제가 없음: paymentId={}", paymentId);
            return PaymentExpirationOutcome.SKIPPED;
        }
        Payment payment = found.get();
        if (payment.getStatus() != PaymentStatus.IN_PROGRESS) {
            log.info("이미 끝난 결제라 만료 확인하지 않음: paymentId={}, status={}", paymentId, payment.getStatus());
            return PaymentExpirationOutcome.SKIPPED;
        }
        // 만료 시각 없이 만들어지는 결제(포인트 전액 결제 등)가 이 작업으로 들어오면 기준이 없어 판단하지 않는다
        if (payment.getExpiresAt() == null) {
            return manualReview(payment, "만료 시각이 없는 IN_PROGRESS 결제");
        }
        // 큐가 일찍 꺼낸 작업. 더 이른 예약을 남기는 enqueueIfEarlier로는 같은 이른 작업이 계속 다시 실행되므로 덮어쓴다
        if (LocalDateTime.now(clock).isBefore(payment.getExpiresAt())) {
            Instant executeAt = payment.getExpiresAt().atZone(clock.getZone()).toInstant();
            reschedule(paymentId, executeAt, "만료 시각 전 실행");
            return PaymentExpirationOutcome.RESCHEDULED;
        }

        try {
            return checkPortOne(payment);
        } catch (TransientDataAccessException e) {
            // 락 대기 초과·쿼리 timeout처럼 다시 하면 풀릴 수 있는 DB 장애
            log.warn("일시적 DB 장애로 결제 만료 재조회: paymentId={}", paymentId, e);
            return retry(payment, "일시적 DB 장애: " + e.getClass().getSimpleName());
        } catch (RuntimeException e) {
            // 코드 버그·예상 못한 상태. 1분마다 같은 오류를 반복하지 않는다. Fallback이 시간 단위로 다시 넣는다
            log.error("결제 만료 처리 내부 오류: paymentId={}", paymentId, e);
            return manualReview(payment, "예상하지 못한 내부 오류: " + e.getClass().getSimpleName());
        }
    }

    private PaymentExpirationOutcome checkPortOne(Payment payment) {
        // 락 없이 PortOne을 묻는다. 404 판정은 이 조회에서 온 오류에만 적용한다 — Processor의 결제 조회 실패와 섞지 않는다
        PortOnePayment portOnePayment;
        try {
            portOnePayment = portOnePaymentClient.getPayment(payment.getPortonePaymentId());
        } catch (BusinessException e) {
            ErrorCode errorCode = e.getErrorCode();
            if (errorCode == PaymentErrorCode.PAYMENT_NOT_FOUND) {
                // 결제창을 열지 않아 PG에 결제 자체가 없다. 이후 승인되더라도 늦은 승인 경로가 돈을 돌려준다
                return paymentExpirationProcessor.expire(payment.getId());
            }
            if (errorCode == CommonErrorCode.EXTERNAL_SERVICE_ERROR
                || errorCode == CommonErrorCode.EXTERNAL_SERVICE_TIMEOUT) {
                // 5xx·timeout·연결 실패·응답 처리 실패. 클라이언트가 WARN을 남겼다. 실패로 단정하지 않고 다시 묻는다
                return retry(payment, "PortOne 조회 실패: " + errorCode.getCode());
            }
            return manualReview(payment, "PortOne 조회의 예상하지 못한 오류: " + errorCode.getCode());
        }

        return switch (portOnePayment.status()) {
            // 결제창만 열리고 승인되지 않았다
            case READY -> paymentExpirationProcessor.expire(payment.getId());
            case PAID, FAILED -> applyConfirmResult(payment, portOnePayment);
            // 카드 결제에서 예상하지 않는 상태라 실패로 단정하지 않고 다시 묻는다
            case PENDING, VIRTUAL_ACCOUNT_ISSUED, UNKNOWN ->
                retry(payment, "PG 결제 미확정: status=" + portOnePayment.status());
            // 내부 결제가 아직 IN_PROGRESS인데 PG에서 취소된 경우는 주문을 종료할 근거가 부족하므로 자동 반영하지 않는다
            case CANCELED, PARTIAL_CANCELED -> manualReview(payment, "PG에서 취소된 미확정 결제: status="
                + portOnePayment.status());
        };
    }

    // 확정 API·웹훅과 같은 Processor로 판정한다. 만료 뒤 승인·금액 불일치면 SYSTEM 환불을 만들고 그 실행 예약까지 잇는다
    private PaymentExpirationOutcome applyConfirmResult(Payment payment, PortOnePayment portOnePayment) {
        PaymentApprovalOutcome approvalOutcome = paymentConfirmProcessor.process(payment.getOrderId(), portOnePayment);
        log.info("결제 만료 확인 중 확정 판정: paymentId={}, outcome={}", payment.getId(), approvalOutcome);
        // 어느 판정이 SYSTEM 환불을 만드는지는 scheduleAfterConfirm이 정한다. 여기서 다시 거르지 않는다
        systemRefundExecutionFacade.scheduleAfterConfirm(payment.getId(), approvalOutcome);
        return switch (approvalOutcome) {
            // 승인·PG 실패·만료 뒤 승인·금액 불일치를 반영했거나, 다른 경로가 먼저 끝냈다
            case APPROVED, ALREADY_PAID, PG_FAILED, AMOUNT_MISMATCH, EXPIRED, ALREADY_CLOSED ->
                PaymentExpirationOutcome.RESOLVED;
            // PAID인데 금액·완료 시각이 비어 있다. 반영이 늦는 경우라 다시 묻는다
            case NOT_PAID -> retry(payment, "PG 결제 근거 부족: status=" + portOnePayment.status());
            // 다른 주문의 결제를 자동으로 반영하면 안 된다
            case ORDER_MISMATCH -> manualReview(payment, "customData 주문 ID 불일치: "
                + portOnePayment.customDataOrderId());
            // 잠금 아래에서 다시 보니 다른 흐름이 결제 상태를 가져갔다
            case INVALID_STATUS -> PaymentExpirationOutcome.SKIPPED;
        };
    }

    // 만료 시각부터 RECHECK_WINDOW 안이면 1분 뒤 다시 묻는다. 지나면 고빈도 재조회를 멈추고 Fallback 주기에 맡긴다
    private PaymentExpirationOutcome retry(Payment payment, String reason) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (!now.isBefore(payment.getExpiresAt().plus(RECHECK_WINDOW))) {
            // 자동으로 실패 처리하지 않는다. 결제가 IN_PROGRESS로 남아 Fallback이 시간 단위로 다시 넣는다
            log.error("결제 만료 확인이 {}분 넘게 미확정: paymentId={}, orderId={}, reason={}",
                RECHECK_WINDOW.toMinutes(), payment.getId(), payment.getOrderId(), reason);
            return PaymentExpirationOutcome.STALLED;
        }
        reschedule(payment.getId(), clock.instant().plus(RECHECK_DELAY), reason);
        return PaymentExpirationOutcome.RESCHEDULED;
    }

    // 다시 볼 시각을 정확히 그 시각으로 덮어쓴다. 1분 재조회와 만료 시각 재예약 모두 뒤로 미는 경우다
    private void reschedule(Long paymentId, Instant executeAt, String reason) {
        try {
            paymentDelayQueue.reschedule(PaymentDelayTask.paymentExpire(paymentId), executeAt);
            log.info("결제 만료 재조회 예약: paymentId={}, executeAt={}, reason={}", paymentId, executeAt, reason);
        } catch (DataAccessException e) {
            // 결제는 IN_PROGRESS 그대로라 Fallback이 다시 등록한다
            log.error("결제 만료 재조회 예약 실패: paymentId={}", paymentId, e);
        }
    }

    // 결제는 IN_PROGRESS로 남는다. 재예약하지 않고 운영 확인을 요청하며, Fallback이 시간 단위로 다시 넣는다
    private PaymentExpirationOutcome manualReview(Payment payment, String reason) {
        log.error("결제 만료 수동 확인 필요: paymentId={}, orderId={}, reason={}",
            payment.getId(), payment.getOrderId(), reason);
        return PaymentExpirationOutcome.MANUAL_REVIEW;
    }
}
