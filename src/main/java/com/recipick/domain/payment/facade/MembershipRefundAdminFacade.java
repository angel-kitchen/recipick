package com.recipick.domain.payment.facade;

import com.recipick.domain.member.service.MembershipBillingService;
import com.recipick.domain.payment.dto.response.MembershipRefundRecheckResponse;
import com.recipick.domain.payment.dto.response.MembershipRefundRetryResponse;
import com.recipick.domain.payment.entity.MembershipRefund;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.repository.MembershipRefundRepository;
import com.recipick.domain.payment.service.MembershipRefundAcceptResult;
import com.recipick.domain.payment.service.MembershipRefundCheckResult;
import com.recipick.domain.payment.service.MembershipRefundExecutionResult;
import com.recipick.domain.payment.service.MembershipRefundRef;
import com.recipick.domain.payment.service.PaymentDelayQueue;
import com.recipick.domain.payment.service.PaymentDelayTask;
import com.recipick.global.exception.BusinessException;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 멤버십 환불 운영 재조회·SYSTEM 재실행(SUPER_ADMIN). 둘 다 내부 변경·감사 TX 커밋 → PG(트랜잭션 밖) → 최신 상태 응답 순이다.
// 재조회는 요청 접수 감사를 커밋한 뒤 GET 1회로 관측값을 그대로 돌려주고(강한 차단 행은 관측만), 재실행은 FAILED SYSTEM 환불의 후속 행
// 접수와 재실행 감사를 한 TX로 커밋한 뒤 큐 예약 + 동기 실행 1회다(기존 후속 행 반환·거절은 새 실행이 아니라 감사 없음).
// 운영자는 금액·결제 ID·취소 ID를 입력하지 않는다. 표시 해제·강제 완료는 없다
@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipRefundAdminFacade {

    private final MembershipRefundRepository refunds;
    private final MembershipBillingService membershipBillingService;
    private final MembershipRefundAdminAuditProcessor auditProcessor;
    private final MembershipRefundRetryAuditProcessor retryAuditProcessor;
    private final MembershipRefundExecutionFacade executionFacade;
    private final PaymentDelayQueue paymentDelayQueue;
    private final Clock clock;

    @Transactional(propagation = Propagation.NEVER)
    public MembershipRefundRecheckResponse recheck(Long refundId) {
        auditProcessor.recordRecheck(refundId);
        MembershipRefundCheckResult check = executionFacade.recheck(refundId);
        return MembershipRefundRecheckResponse.of(reload(refundId), check);
    }

    @Transactional(propagation = Propagation.NEVER)
    public MembershipRefundRetryResponse retry(Long failedRefundId) {
        // 회원 ID는 TX 밖에서 역조회한다 — 접수·감사 TX의 첫 DB 작업이 회원 잠금이어야 한다
        MembershipRefundRef ref = refunds.findRef(failedRefundId)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.MEMBERSHIP_REFUND_NOT_FOUND));
        Long memberId = membershipBillingService.findMemberIdOfMembership(ref.membershipId());
        MembershipRefundAcceptResult accepted = retryAuditProcessor.acceptAndRecord(memberId, failedRefundId);
        if (accepted.isRejected()) {
            throw new BusinessException(PaymentErrorCode.MEMBERSHIP_REFUND_RETRY_NOT_ALLOWED);
        }
        MembershipRefundExecutionResult executed = null;
        if (accepted.requiresExecution()) {
            enqueue(accepted.refundId());
            executed = executionFacade.execute(accepted.refundId());
            log.info("SYSTEM 환불 재실행 동기 시도: failedRefundId={}, retryRefundId={}, result={}", failedRefundId,
                accepted.refundId(), executed);
        }
        return MembershipRefundRetryResponse.of(reload(accepted.refundId()), accepted.existing(), executed);
    }

    private void enqueue(Long refundId) {
        try {
            paymentDelayQueue.enqueueIfEarlier(PaymentDelayTask.membershipRefundExecute(refundId), clock.instant());
        } catch (RuntimeException e) {
            log.warn("환불 실행 예약 실패(DB 복구가 받는다): refundId={}, cause={}", refundId, e.getClass().getSimpleName());
        }
    }

    private MembershipRefund reload(Long refundId) {
        return refunds.findWithAttemptById(refundId)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.MEMBERSHIP_REFUND_NOT_FOUND));
    }
}
