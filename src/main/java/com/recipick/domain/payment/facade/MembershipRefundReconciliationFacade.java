package com.recipick.domain.payment.facade;

import com.recipick.domain.payment.entity.WebhookEvent;
import com.recipick.domain.payment.repository.MembershipPaymentAttemptRepository;
import com.recipick.domain.payment.repository.MembershipRefundRepository;
import com.recipick.domain.payment.service.MembershipPaymentAttemptRef;
import com.recipick.domain.payment.service.MembershipRefundExecutionResult;
import com.recipick.domain.payment.service.MembershipWebhookResult;
import com.recipick.global.exception.BusinessException;
import com.recipick.infra.portone.PortOnePaymentClient;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOneWebhookType;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 멤버십 결제 ID의 취소 계열 웹훅(CANCELED·PARTIAL_CANCELED·CANCEL_PENDING). 웹훅 본문은 믿지 않는다 — 시도 매핑 → GET 1회 →
// 그 시도의 환불들에 관측 반영(POST 없음). 환불 행이 하나도 없는 시도의 취소는 외부 취소라 자동 귀속하지 않고 운영 확인으로 남긴다.
// Inbox 기록(TX-C)은 기존 Inbox가 한다: 완료·실패 확정·멱등 → PROCESSED / 결과 불명·조회 장애·처리 중 → RETRY_PENDING /
// 매핑 없음·외부 취소·차단 → MANUAL_REVIEW
@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipRefundReconciliationFacade {

    public static final String REASON_EXTERNAL_CANCELLATION_UNEXPLAINED = "EXTERNAL_CANCELLATION_UNEXPLAINED";

    private final MembershipPaymentAttemptRepository attempts;
    private final MembershipRefundRepository refunds;
    private final MembershipRefundExecutionFacade executionFacade;
    private final PortOnePaymentClient portOnePaymentClient;

    public MembershipWebhookResult reconcileWebhook(WebhookEvent event, PortOneWebhookType type) {
        Optional<MembershipPaymentAttemptRef> ref = attempts.findRefByPortonePaymentId(event.getPortonePaymentId());
        if (ref.isEmpty()) {
            log.error("멤버십 결제 ID 취소 웹훅인데 시도가 없음(운영 확인): webhookEventId={}", event.getId());
            return MembershipWebhookResult.manualReview("멤버십 시도 매핑 없음");
        }
        Long attemptId = ref.get().attemptId();
        if (refunds.findIdsByMembershipPaymentAttemptIdOrderByIdAsc(attemptId).isEmpty()) {
            log.error("환불 요청이 없는 시도에 취소 웹훅(외부 취소, 운영 확인): webhookEventId={}, attemptId={}, type={}",
                event.getId(), attemptId, type);
            return MembershipWebhookResult.manualReview(REASON_EXTERNAL_CANCELLATION_UNEXPLAINED);
        }
        PortOnePayment payment;
        try {
            payment = portOnePaymentClient.getPayment(ref.get().portonePaymentId());
        } catch (BusinessException e) {
            return MembershipWebhookResult.retry("PG 조회 실패: " + e.getErrorCode().getCode());
        }
        MembershipRefundExecutionResult result = executionFacade.reconcileObserved(attemptId, payment);
        log.info("멤버십 취소 웹훅 환불 정합화: webhookEventId={}, attemptId={}, result={}", event.getId(), attemptId, result);
        return switch (result) {
            case COMPLETED, FAILED, SKIPPED -> MembershipWebhookResult.processed();
            case PENDING -> MembershipWebhookResult.retry("환불 결과 미확정");
            case REVIEW_REQUIRED -> MembershipWebhookResult.manualReview("환불 운영 확인 대상");
        };
    }
}
