package com.recipick.domain.payment.facade;

import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.service.MembershipBillingService;
import com.recipick.domain.payment.entity.WebhookEvent;
import com.recipick.domain.payment.repository.MembershipPaymentAttemptRepository;
import com.recipick.domain.payment.service.MembershipChargeOutcome;
import com.recipick.domain.payment.service.MembershipPaymentAttemptRef;
import com.recipick.domain.payment.service.MembershipReconciliationTrigger;
import com.recipick.domain.payment.service.MembershipWebhookResult;
import com.recipick.global.exception.BusinessException;
import com.recipick.infra.portone.dto.PortOneWebhookType;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 웹훅 Inbox 한 건 중 멤버십 결제 ID의 분기. 이벤트 claim(TX-A)과 결과 기록(TX-C)은 기존 Inbox가 하고, 여기서는 매핑·판정만 한다 —
// 이벤트 행을 잠근 채 회원 잠금을 기다리지 않는다. 결제 ID의 접두어는 라우팅 힌트일 뿐이고 시도 매핑은 UNIQUE 결제 ID 정확 일치다.
// 결제·실패 웹훅은 같은 결제 ID 재조회(검증된 웹훅 원인)로 실행기에 넘긴다 — 웹훅 본문의 상태는 믿지 않고 POST도 하지 않는다.
// 취소 계열 웹훅은 환불 반영 단계가 처리하도록 운영 확인 사유로 보존한다(환불 완료·이용권 종료를 여기서 반영하지 않는다).
// 일시적 DB 장애·그 밖의 예외는 잡지 않는다 — 기존 Inbox의 처리(재시도·운영 확인)를 그대로 쓴다
@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipWebhookReconciliationFacade {

    public static final String REASON_REFUND_RECONCILIATION_PENDING = "MEMBERSHIP_REFUND_RECONCILIATION_PENDING";

    private final MembershipPaymentAttemptRepository attempts;
    private final MembershipBillingService membershipBillingService;
    private final MembershipChargeExecutor executor;

    public MembershipWebhookResult reconcile(WebhookEvent event, PortOneWebhookType type) {
        Optional<MembershipPaymentAttemptRef> ref = attempts.findRefByPortonePaymentId(event.getPortonePaymentId());
        if (ref.isEmpty()) {
            log.error("멤버십 결제 ID 웹훅인데 시도가 없음(운영 확인): webhookEventId={}", event.getId());
            return MembershipWebhookResult.manualReview("멤버십 시도 매핑 없음");
        }
        if (type != PortOneWebhookType.PAID && type != PortOneWebhookType.FAILED) {
            return MembershipWebhookResult.manualReview(REASON_REFUND_RECONCILIATION_PENDING);
        }
        Long memberId;
        try {
            memberId = membershipBillingService.findMemberIdOfMembership(ref.get().membershipId());
        } catch (BusinessException e) {
            if (e.getErrorCode() == MemberErrorCode.ACTIVE_MEMBERSHIP_NOT_FOUND) {
                log.error("멤버십 웹훅 시도의 이용권이 없음(운영 확인): webhookEventId={}, attemptId={}", event.getId(),
                    ref.get().attemptId());
                return MembershipWebhookResult.manualReview("이용권 매핑 없음");
            }
            throw e;
        }
        MembershipChargeOutcome outcome = executor.reconcile(memberId, ref.get().attemptId(),
            MembershipReconciliationTrigger.VERIFIED_WEBHOOK);
        log.info("멤버십 웹훅 재조회 판정: webhookEventId={}, attemptId={}, outcome={}", event.getId(),
            ref.get().attemptId(), outcome);
        return toResult(outcome);
    }

    private static MembershipWebhookResult toResult(MembershipChargeOutcome outcome) {
        return switch (outcome) {
            case EXTENDED, ACTIVATED, ALREADY_APPLIED, UNAPPLIED_REFUND_REQUESTED, CLOSED_UNPAID, RETRY_WAITING,
                 RENEWAL_STOPPED, PAYMENT_METHOD_REQUIRED -> MembershipWebhookResult.processed();
            case STILL_UNKNOWN, IN_PROGRESS, NOT_DISPATCHED -> MembershipWebhookResult.retry(outcome.name());
            case MANUAL_REVIEW, RECONCILE_HALTED -> MembershipWebhookResult.manualReview(outcome.name());
            case RETRY_READY -> MembershipWebhookResult.manualReview("재조회에서 나올 수 없는 결과");
        };
    }
}
