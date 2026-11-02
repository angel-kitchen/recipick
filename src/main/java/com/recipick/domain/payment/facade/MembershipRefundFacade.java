package com.recipick.domain.payment.facade;

import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.payment.dto.response.MembershipRefundResult;
import com.recipick.domain.payment.entity.MembershipRefund;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.service.MembershipRefundAcceptResult;
import com.recipick.domain.payment.service.MembershipRefundExecutionResult;
import com.recipick.domain.payment.service.MembershipRequestHash;
import com.recipick.domain.payment.service.PaymentDelayQueue;
import com.recipick.domain.payment.service.PaymentDelayTask;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import java.time.Clock;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

// 회원 즉시 해지 환불의 공개 진입점. 회원 도메인의 terminate API가 Idempotency-Key와 함께 부른다(회원 Facade에는 바깥 TX를 걸지 않는다).
// 키 검증 → 접수 TX(회원 잠금·자격·회차 선택·계산·저장·0원 완료) → 커밋 → 신규 양수 접수면 큐 예약 + 실행기 1회 동기 시도(PG는 TX 밖) →
// 최신 행을 짧은 조회 TX로 읽어 응답. 같은 키의 재요청·0원 완료·거절에는 PG 호출이 없다.
// 거절은 접수 TX가 결과로 돌려준 것을 커밋 뒤 BusinessException으로 바꾼다(차단 표시가 함께 커밋되게)
@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipRefundFacade {

    private static final int MAX_KEY_LENGTH = 64;

    private final MembershipRefundRequestProcessor requestProcessor;
    private final MembershipRefundExecutionFacade executionFacade;
    private final PaymentDelayQueue paymentDelayQueue;
    private final Clock clock;

    @Transactional(propagation = Propagation.NEVER)
    public MembershipRefundResult requestMemberRefund(Long memberId, Long membershipId, String idempotencyKey) {
        if (membershipId == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        String storedKey = storedKey(memberId, idempotencyKey);
        MembershipRefundAcceptResult accepted = requestProcessor.acceptMember(memberId, membershipId, storedKey,
            MembershipRequestHash.ofRefund(membershipId));
        if (accepted.isRejected()) {
            throw new BusinessException(switch (accepted.rejectReason()) {
                case NOT_TERMINABLE -> MemberErrorCode.NOT_TERMINABLE;
                case PAYMENT_IN_PROGRESS -> MemberErrorCode.CANCEL_PAYMENT_IN_PROGRESS;
                case SETTLEMENT_BLOCKED -> PaymentErrorCode.MEMBERSHIP_REFUND_SETTLEMENT_BLOCKED;
                case RETRY_NOT_ALLOWED -> PaymentErrorCode.MEMBERSHIP_REFUND_RETRY_NOT_ALLOWED;
            });
        }
        if (accepted.requiresExecution()) {
            enqueue(accepted.refundId());
            MembershipRefundExecutionResult executed = executionFacade.execute(accepted.refundId());
            log.info("즉시 해지 환불 동기 실행: refundId={}, result={}", accepted.refundId(), executed);
        }
        return requestProcessor.memberResult(accepted.refundId());
    }

    // 접수 커밋 뒤 큐 예약(동기 실행이 중간에 멈춰도 Worker가 이어 간다). 예약 실패는 로그만 — DB 복구가 받는다
    private void enqueue(Long refundId) {
        try {
            paymentDelayQueue.enqueueIfEarlier(PaymentDelayTask.membershipRefundExecute(refundId), clock.instant());
        } catch (RuntimeException e) {
            log.warn("환불 실행 예약 실패(DB 복구가 받는다): refundId={}, cause={}", refundId, e.getClass().getSimpleName());
        }
    }

    // 키 필수(COMMON-007), 1~64자·공백 없음(COMMON-001), 소문자 정규화, 저장 키 "mrf_member:{memberId}:{key}"
    private static String storedKey(Long memberId, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BusinessException(CommonErrorCode.MISSING_IDEMPOTENCY_KEY);
        }
        String key = idempotencyKey.trim();
        if (key.length() > MAX_KEY_LENGTH || key.chars().anyMatch(Character::isWhitespace)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return MembershipRefund.MEMBER_KEY_PREFIX + memberId + ":" + key.toLowerCase(Locale.ROOT);
    }
}
