package com.recipick.domain.payment.facade;

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.payment.dto.AdminRefundExecution;
import com.recipick.domain.payment.dto.MartRejectionPaymentResult;
import com.recipick.domain.payment.dto.MartRejectionPreparation;
import com.recipick.domain.payment.service.MartRejectionOutcome;
import com.recipick.domain.payment.service.RefundReconcileOutcome;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.global.exception.ErrorCode;
import java.util.Optional;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

// 마트 주문 거절 API의 진입점. 트랜잭션을 걸지 않는다 — TX1(접수) 커밋 뒤에 PortOne 호출이 있기 때문.
// PortOne 조회·취소와 TX2는 Worker·Fallback과 같은 실행기(AdminRefundExecutionFacade)에 맡기고 여기서는 직접 부르지 않는다.
// 실패 outcome의 예외는 TX2가 커밋된 뒤 여기서 던진다 — Processor 안에서 던지면 결제 PAID 복귀·환불 FAILED가 롤백된다
@Slf4j
@Component
@RequiredArgsConstructor
public class MartRejectionFacade {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;

    // 회원 취소 TX1과 같은 정책. 잠금 실패 뒤 거절 접수 트랜잭션을 처음부터 다시 시작하는 횟수(최초 포함)
    private static final int MAX_LOCK_ATTEMPTS = 2;

    // 교착 상대가 롤백·커밋을 끝낼 틈
    private static final long LOCK_RETRY_BACKOFF_MILLIS = 100L;

    private final MartRejectionProcessor martRejectionProcessor;
    private final AdminRefundExecutionFacade adminRefundExecutionFacade;

    // 재시도가 매번 새 트랜잭션이려면 바깥 트랜잭션이 없어야 한다. 누가 트랜잭션 안에서 부르면 롤백된 트랜잭션에 합류하므로 막는다
    @Transactional(propagation = Propagation.NEVER)
    public MartRejectionPaymentResult reject(AdminRole role, Long adminMartId, Long adminId, Long orderId,
                                             String idempotencyKey, String reason) {
        validateIdempotencyKey(idempotencyKey);

        MartRejectionPreparation preparation =
            prepareWithRetry(role, adminMartId, adminId, orderId, idempotencyKey, reason);
        MartRejectionPaymentResult result = preparation.requiresExecution()
            ? execute(preparation)
            : preparation.result();

        // 여기 도달했으면 TX2는 이미 커밋됐다. 지금 던져도 결제 PAID 복귀·환불 FAILED는 남는다
        Optional<ErrorCode> errorCode = result.outcome().errorCode();
        if (errorCode.isPresent()) {
            throw new BusinessException(errorCode.get());
        }
        return result;
    }

    @Transactional(propagation = Propagation.NEVER)
    public MartRejectionPaymentResult retry(AdminRole role, Long adminMartId, Long orderId,
                                             Long originalRefundId, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        if (idempotencyKey.chars().anyMatch(Character::isWhitespace)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        MartRejectionPreparation preparation = martRejectionProcessor.prepareRetry(role, adminMartId, orderId,
            originalRefundId, idempotencyKey.toLowerCase(Locale.ROOT));
        MartRejectionPaymentResult result = preparation.requiresExecution() ? execute(preparation) : preparation.result();
        result.outcome().errorCode().ifPresent(code -> { throw new BusinessException(code); });
        return result;
    }

    private MartRejectionPaymentResult execute(MartRejectionPreparation preparation) {
        Long refundId = preparation.refundId();
        // TX1은 커밋됐다. 이어지는 실행이 서버 중단으로 끝나지 못해도 10분 뒤 Worker가 이어받도록 먼저 등록한다
        adminRefundExecutionFacade.scheduleSafetyNet(refundId);
        AdminRefundExecution execution = adminRefundExecutionFacade.execute(refundId);
        if (execution.appliedResult().isPresent()) {
            return execution.applied();
        }

        // 이번 실행에서 TX2가 돌지 않았다. 웹훅이 먼저 끝냈거나 조회 장애로 미뤄졌을 수 있어 현재 상태로 답한다
        MartRejectionPaymentResult current =
            martRejectionProcessor.getCurrentResult(preparation.result().response().paymentId(), refundId);
        // PG 취소는 성공했는데 환불이 FAILED로 남은 경합일 수 있다. 그 FAILED를 409로 확정해 답하지 않고
        // 확인 중(202)으로 돌려준다. 운영 확인은 실행기가 남긴 ERROR 로그로 이어진다
        if (execution.outcome() == RefundReconcileOutcome.MANUAL_REVIEW) {
            return new MartRejectionPaymentResult(MartRejectionOutcome.PENDING, current.response());
        }
        return current;
    }

    // TX1은 PG 호출 전이라 잠금 실패로 롤백돼도 돈이 움직이지 않는다. 새 트랜잭션에서 prepare를 처음부터 다시 불러
    // 권한·결제 잠금·같은 키 판정·replay 검증·UNIQUE 판정을 모두 다시 거친다
    private MartRejectionPreparation prepareWithRetry(AdminRole role, Long adminMartId, Long adminId, Long orderId,
                                                      String idempotencyKey, String reason) {
        for (int attempt = 1; ; attempt++) {
            try {
                return martRejectionProcessor.prepare(role, adminMartId, adminId, orderId, idempotencyKey, reason);
            } catch (PessimisticLockingFailureException e) {
                if (attempt >= MAX_LOCK_ATTEMPTS || !pauseBeforeRetry()) {
                    log.warn("마트 거절 접수(TX1) 잠금 실패 — 재시도 소진: orderId={}, attempts={}, {}",
                        orderId, attempt, LockFailureDetail.of(e), e);
                    throw new BusinessException(CommonErrorCode.LOCK_ACQUISITION_TIMEOUT, e);
                }
                log.warn("마트 거절 접수(TX1) 잠금 실패 — 새 트랜잭션으로 재시도: orderId={}, attempt={}, {}",
                    orderId, attempt, LockFailureDetail.of(e));
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
