package com.recipick.domain.payment.facade;

import com.recipick.domain.payment.dto.PaymentCancelPreparation;
import com.recipick.domain.payment.dto.PaymentCancelResult;
import com.recipick.domain.payment.service.PaymentCancelOutcome;
import com.recipick.domain.payment.service.PaymentDelayQueue;
import com.recipick.domain.payment.service.PaymentDelayTask;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.global.exception.ErrorCode;
import com.recipick.infra.portone.PortOnePaymentClient;
import com.recipick.infra.portone.dto.PortOneCancelResult;
import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

// 결제 취소 API의 진입점. 트랜잭션을 걸지 않는다. ㅡ 가운데 PortOne 호출이 있기 때문
// TX1(접수) -> PortOne 전액 취소 -> TX2(반영) 순이고, 결제 전 취소는 TX1에서 끝난다
// 응답 본문은 Processor가 트랜잭션 안에서 만든 것을 그대로 쓴다. 실패 outcome의 예외는 TX2가 커밋된 뒤 여기서 던진다 —
// Processor 안에서 던지면 PAID 복귀·REFUND FAILED가 롤백된다.
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCancelFacade {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;

    // PortOne에는 클라이언트 키 대신 환불 행마다 고정된 서버 키를 보낸다. PortOne의 길이·문자 제약이 레시픽 API로 새지 않는다
    private static final String PORTONE_IDEMPOTENCY_KEY_PREFIX = "recipick-refund-";

    // 잠금 실패 뒤 같은 트랜잭션을 처음부터 다시 시작하는 횟수(최초 포함). 회차마다 잠금 대기 한도(5초)를 다시 쓰므로 늘리지 않는다
    private static final int MAX_LOCK_ATTEMPTS = 2;

    // 교착 상대가 롤백·커밋을 끝낼 틈. 바로 다시 들어가면 같은 순서로 다시 부딪히기 쉽다
    private static final long LOCK_RETRY_BACKOFF_MILLIS = 100L;

    private final PaymentCancelProcessor paymentCancelProcessor;
    private final PortOnePaymentClient portOnePaymentClient;
    private final PaymentDelayQueue paymentDelayQueue;
    private final Clock clock;

    // 재시도가 매번 새 트랜잭션이려면 바깥 트랜잭션이 없어야 한다. 누가 트랜잭션 안에서 부르면 롤백된 트랜잭션에 합류하므로 막는다
    @Transactional(propagation = Propagation.NEVER)
    public PaymentCancelResult cancel(Long memberId, Long paymentId, String idempotencyKey, String reason) {
        validateIdempotencyKey(idempotencyKey);

        PaymentCancelPreparation preparation = prepareWithRetry(memberId, paymentId, idempotencyKey, reason);
        PaymentCancelResult result = preparation.finishedResult();

        if (preparation.requiresPortOneCall()) {
            // TX1은 커밋되고 잠금은 풀렸다. 여기서 PortOne을 기다려도 같은 결제의 다른 요청이 락에 묶이지 않는다
            PortOneCancelResult portOneResult = portOnePaymentClient.cancelPayment(
                preparation.portonePaymentId(),
                PORTONE_IDEMPOTENCY_KEY_PREFIX + preparation.refundId(),
                reason,
                preparation.amount());
            result = paymentCancelProcessor.apply(paymentId, preparation.refundId(), portOneResult);
            // 결과를 확정하지 못했다. 웹훅만 기다리지 않고 환불 재확인을 예약한다.
            // TX2를 거치지 않은 같은 키 재요청(PENDING 재현)은 첫 요청이 이미 예약했고, 놓쳤어도 Fallback이 다시 넣는다
            if (result.outcome() == PaymentCancelOutcome.PENDING) {
                scheduleRefundReconcile(preparation.refundId());
            }
        }

        // 여기 도달했으면 TX2는 이미 커밋됐다. 지금 던져도 PAID 복귀·REFUND FAILED는 남는다
        Optional<ErrorCode> errorCode = result.outcome().errorCode();
        if (errorCode.isPresent()) {
            throw new BusinessException(errorCode.get());
        }
        return result;
    }

    // TX1은 PG 호출 전이라 잠금 실패로 롤백돼도 돈이 움직이지 않는다. 새 트랜잭션에서 prepare를 처음부터 다시 불러
    // 결제 잠금·소유권·같은 키 판정·replay 검증·UNIQUE 판정을 모두 다시 거친다 — 실패한 회차의 조회 결과는 쓰지 않는다
    private PaymentCancelPreparation prepareWithRetry(Long memberId, Long paymentId, String idempotencyKey,
                                                      String reason) {
        for (int attempt = 1; ; attempt++) {
            try {
                return paymentCancelProcessor.prepare(memberId, paymentId, idempotencyKey, reason);
            } catch (PessimisticLockingFailureException e) {
                if (attempt >= MAX_LOCK_ATTEMPTS || !pauseBeforeRetry()) {
                    log.warn("결제 취소 접수(TX1) 잠금 실패 — 재시도 소진: paymentId={}, attempts={}, {}",
                        paymentId, attempt, LockFailureDetail.of(e), e);
                    throw new BusinessException(CommonErrorCode.LOCK_ACQUISITION_TIMEOUT, e);
                }
                log.warn("결제 취소 접수(TX1) 잠금 실패 — 새 트랜잭션으로 재시도: paymentId={}, attempt={}, {}",
                    paymentId, attempt, LockFailureDetail.of(e));
            }
        }
    }

    // 인터럽트되면 더 기다리지 않는다. 호출자가 재시도를 멈추고 소진과 같은 길로 간다
    private boolean pauseBeforeRetry() {
        try {
            Thread.sleep(LOCK_RETRY_BACKOFF_MILLIS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // 웹훅이 같은 환불을 먼저 등록했을 수 있어 더 이른 예약은 그대로 둔다. 등록 실패로 취소 응답(202)을 바꾸지 않는다 —
    // 환불이 REQUESTED로 남아 Fallback이 다시 등록한다
    private void scheduleRefundReconcile(Long refundId) {
        try {
            paymentDelayQueue.enqueueIfEarlier(PaymentDelayTask.refundReconcile(refundId),
                clock.instant().plus(RefundReconciliationFacade.INITIAL_DELAY));
        } catch (DataAccessException e) {
            log.error("환불 재확인 예약 실패: refundId={}", refundId, e);
        }
    }

    // 헤더가 아예 없으면 GlobalExceptionHandler가 COMMON-007로 바꾼다. 여기서는 빈 값과 길이를 막는다
    private void validateIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BusinessException(CommonErrorCode.MISSING_IDEMPOTENCY_KEY);
        }
        if (idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }
}
