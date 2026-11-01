package com.recipick.domain.payment.facade;

import com.recipick.domain.member.dto.MembershipConsent;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MembershipBillingService;
import com.recipick.domain.payment.config.MembershipBillingGate;
import com.recipick.domain.payment.config.MembershipBillingProperties;
import com.recipick.domain.payment.entity.BillingKey;
import com.recipick.domain.payment.entity.MembershipPayment;
import com.recipick.domain.payment.entity.MembershipPaymentAttempt;
import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import com.recipick.domain.payment.entity.enums.MembershipPaymentStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.repository.BillingKeyRepository;
import com.recipick.domain.payment.repository.MembershipPaymentAttemptRepository;
import com.recipick.domain.payment.repository.MembershipPaymentRepository;
import com.recipick.domain.payment.service.MembershipBillingPeriods;
import com.recipick.domain.payment.service.MembershipPaymentQueryService;
import com.recipick.domain.payment.service.MembershipSubscribeStatus;
import com.recipick.global.common.PolicyConstants;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 바로 구독 TX1. 첫 DB 작업은 회원 잠금이다.
// ② 멱등 키 조회가 ③ 이용권 중복 검사보다 앞이다 — 첫 요청이 ⑦에서 결제 대기 이용권을 만들었으므로 같은 요청을 다시 보낸 회원이
// ③을 먼저 만나면 자기가 만든 대기 행 때문에 MEMBERSHIP-002를 받는다. 기존 요청은 플래그가 꺼져 있어도 결과를 돌려준다.
// 신규 접수만 플래그(BILLING-009)로 막는다. 오류 코드는 모두 dev MemberErrorCode의 기존 값이다
@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipSubscriptionProcessor {

    private final MemberService memberService;
    private final MembershipBillingService membershipBillingService;
    private final MembershipPaymentQueryService membershipPaymentQueryService;
    private final MembershipPaymentRepository payments;
    private final MembershipPaymentAttemptRepository attempts;
    private final BillingKeyRepository billingKeys;
    private final MembershipBillingGate gate;
    private final MembershipBillingProperties properties;

    // existingStatus == null이면 새 접수(attemptId는 READY 시도). 같은 키 재요청이면 기존 상태(attemptId는 최신 시도,
    // latestAttemptReady는 그 시도가 READY라 실행권을 다시 잡아 볼 수 있는지)
    public record SubscriptionReceipt(Long membershipId, Long attemptId, MembershipSubscribeStatus existingStatus,
                                      boolean latestAttemptReady) {
    }

    @Transactional
    public SubscriptionReceipt accept(Long memberId, MembershipConsent consent, String storedKey, String requestHash,
                                      LocalDateTime now) {
        memberService.lockActiveMember(memberId);                                                        // ①
        Optional<MembershipPayment> existing = payments.findByIdempotencyKey(storedKey);                  // ②
        if (existing.isPresent()) {
            return existingReceipt(memberId, existing.get(), requestHash);
        }
        if (!gate.newChargeEnabled()) {
            throw new BusinessException(PaymentErrorCode.MEMBERSHIP_BILLING_DISABLED);
        }
        if (membershipBillingService.existsBlockingMembership(memberId, now)) {                           // ③
            throw new BusinessException(MemberErrorCode.MEMBERSHIP_ALREADY_ACTIVE);
        }
        List<Long> membershipIds = membershipBillingService.findMembershipIds(memberId);                  // ④
        if (membershipPaymentQueryService.existsUnresolved(membershipIds)) {
            throw new BusinessException(MemberErrorCode.MEMBERSHIP_PAYMENT_IN_PROGRESS);
        }
        // ⑤ BK-P2: 키 상태 변경은 모두 MEMBER 잠금 아래라 이 일반 조회는 최신. 키 행은 잠그지 않는다(FK 참조만)
        Long billingKeyId = billingKeys.findIdByMemberIdAndStatus(memberId, BillingKeyStatus.ACTIVE)
            .orElseThrow(() -> new BusinessException(MemberErrorCode.PAYMENT_METHOD_REQUIRED));
        if (consent == null || consent.consentedAmount() == null                                           // ⑥
            || consent.consentedAmount() != PolicyConstants.MEMBERSHIP_MONTHLY_FEE) {
            throw new BusinessException(MemberErrorCode.AUTOPAY_CONSENT_REQUIRED);
        }
        Long membershipId = membershipBillingService.createPendingSubscription(memberId, consent, now);  // ⑦ C-12a
        membershipBillingService.getBillingMembershipForUpdate(memberId, membershipId);                   //    C-7
        MembershipPayment payment = MembershipPayment.pendingFirst(membershipId, storedKey, requestHash, // ⑧
            PolicyConstants.MEMBERSHIP_MONTHLY_FEE, now.toLocalDate(), now.toLocalDate().plusMonths(1).minusDays(1));
        BillingKey key = billingKeys.getReferenceById(billingKeyId);
        try {
            payments.saveAndFlush(payment);
            MembershipPaymentAttempt attempt =
                attempts.saveAndFlush(MembershipPaymentAttempt.ready(payment, 1, key, now,
                    MembershipBillingPeriods.firstChargeLeaseUntil(now, properties.lease(),
                        MembershipBillingPeriods.firstChargeAcceptUntil(now, properties.firstChargeAcceptWindow()))));
            log.info("바로 구독 접수: memberId={}, membershipId={}, attemptId={}", memberId, membershipId,
                attempt.getId());
            return new SubscriptionReceipt(membershipId, attempt.getId(), null, true);
        } catch (DataIntegrityViolationException e) {
            // 같은 키 동시 요청은 회원 잠금으로 직렬화되므로 정상 흐름에서는 오지 않는다. UNIQUE가 최종 방어
            log.warn("바로 구독 접수 중 무결성 충돌: memberId={}", memberId);
            throw new BusinessException(CommonErrorCode.DUPLICATE_REQUEST_IN_PROGRESS);
        }
    }

    // 같은 키 재요청. 해시가 다르면 COMMON-011. 같으면 소유를 C-7로 방어하고 기존 상태만 돌려준다 — 새 행 생성 금지
    private SubscriptionReceipt existingReceipt(Long memberId, MembershipPayment payment, String requestHash) {
        if (!requestHash.equals(payment.getRequestHash())) {
            throw new BusinessException(CommonErrorCode.IDEMPOTENCY_KEY_PAYLOAD_MISMATCH);
        }
        membershipBillingService.getBillingMembershipForUpdate(memberId, payment.getMembershipId());
        MembershipPaymentAttempt latest = attempts.findTopByMembershipPaymentIdOrderByAttemptNoDesc(payment.getId())
            .orElse(null);
        MembershipSubscribeStatus status = statusOf(payment, latest);
        boolean ready = latest != null && latest.getStatus() == MembershipPaymentAttemptStatus.READY;
        return new SubscriptionReceipt(payment.getMembershipId(), latest == null ? null : latest.getId(), status,
            ready);
    }

    private static MembershipSubscribeStatus statusOf(MembershipPayment payment, MembershipPaymentAttempt latest) {
        if (payment.getStatus() == MembershipPaymentStatus.PAID) {
            return MembershipSubscribeStatus.PAID;
        }
        if (payment.getStatus() == MembershipPaymentStatus.FAILED) {
            return MembershipSubscribeStatus.FAILED;
        }
        if (payment.getStatus() == MembershipPaymentStatus.UNKNOWN
            || payment.getStatus() == MembershipPaymentStatus.MANUAL_REVIEW
            || (latest != null && (latest.getStatus() == MembershipPaymentAttemptStatus.UNKNOWN
            || latest.getStatus() == MembershipPaymentAttemptStatus.MANUAL_REVIEW))) {
            return MembershipSubscribeStatus.UNKNOWN;
        }
        return MembershipSubscribeStatus.IN_PROGRESS;
    }
}
