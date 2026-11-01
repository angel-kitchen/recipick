package com.recipick.domain.payment.facade;

import com.recipick.domain.member.dto.BillingMembershipData;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MembershipBillingService;
import com.recipick.domain.payment.config.MembershipBillingProperties;
import com.recipick.domain.payment.entity.BillingKey;
import com.recipick.domain.payment.entity.MembershipPayment;
import com.recipick.domain.payment.entity.MembershipPaymentAttempt;
import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.domain.payment.repository.BillingKeyRepository;
import com.recipick.domain.payment.repository.MembershipPaymentAttemptRepository;
import com.recipick.domain.payment.repository.MembershipPaymentRepository;
import com.recipick.domain.payment.service.MembershipBillingPeriods;
import com.recipick.domain.payment.service.MembershipPaymentQueryService;
import com.recipick.global.common.PolicyConstants;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

// 체험 → 첫 유료 결제 접수 TX1. 회원별로 MEMBER → 체험 이용권 PK 순서로 잠그고 최신 상태를 다시 확인한다.
// 카드가 없다고 "정상 확인"된 경우만 전환을 종료한다(C-16). 빌링키 조회 자체의 예외는 잡지 않고 전파해 TX를 롤백한다 —
// 카드 없음으로 단정하지 않고 다음 실행에서 다시 본다(LC 4-4·L-31)
@Slf4j
@Component
@RequiredArgsConstructor
public class TrialConversionProcessor {

    private final MemberService memberService;
    private final MembershipBillingService membershipBillingService;
    private final MembershipPaymentQueryService membershipPaymentQueryService;
    private final MembershipPaymentRepository payments;
    private final MembershipPaymentAttemptRepository attempts;
    private final BillingKeyRepository billingKeys;
    private final MembershipBillingProperties properties;

    public record ConversionReceipt(Kind kind, Long attemptId) {

        public enum Kind { CHARGE_READY, CLOSED_NO_CARD, SKIPPED }
    }

    @Transactional
    public ConversionReceipt accept(Long memberId, Long trialMembershipId, LocalDateTime now) {
        memberService.lockActiveMember(memberId);                                                        // ①
        BillingMembershipData trial =
            membershipBillingService.getBillingMembershipForUpdate(memberId, trialMembershipId);         // ②
        if (!isConversionPending(trial, now)) {                                                            // ③
            return new ConversionReceipt(ConversionReceipt.Kind.SKIPPED, null);
        }
        if (membershipPaymentQueryService.existsUnresolved(membershipBillingService.findMembershipIds(memberId))) { // ④
            log.warn("미해결 결제가 있어 체험 전환을 보류: memberId={}, trialMembershipId={}", memberId, trialMembershipId);
            return new ConversionReceipt(ConversionReceipt.Kind.SKIPPED, null);
        }
        Optional<Long> activeKeyId = billingKeys.findIdByMemberIdAndStatus(memberId, BillingKeyStatus.ACTIVE); // ⑤
        if (activeKeyId.isEmpty()) {
            membershipBillingService.closeTrialConversion(trialMembershipId, now);                       // C-16
            log.info("체험 종료 시 ACTIVE 카드 없음 → 전환 종료(일반 회원): memberId={}", memberId);
            return new ConversionReceipt(ConversionReceipt.Kind.CLOSED_NO_CARD, null);
        }
        if (trial.consent() == null || trial.consent().consentedAmount() == null                           // ⑥ Q-4
            || trial.consent().consentedAmount() != PolicyConstants.MEMBERSHIP_MONTHLY_FEE
            || !StringUtils.hasText(trial.consent().consentTextVersion())) {
            log.error("체험 동의 금액·문구가 청구 조건과 달라 전환을 접수하지 않음(운영 확인): memberId={}, "
                    + "trialMembershipId={}, consentedAmount={}", memberId, trialMembershipId,
                trial.consent() == null ? null : trial.consent().consentedAmount());
            return new ConversionReceipt(ConversionReceipt.Kind.SKIPPED, null);
        }
        Long paidMembershipId = membershipBillingService.createPendingConversion(memberId, trialMembershipId, now); // ⑦
        membershipBillingService.getBillingMembershipForUpdate(memberId, paidMembershipId);
        MembershipPayment payment = payments.saveAndFlush(MembershipPayment.pendingFirst(                // ⑧
            paidMembershipId, null, null, PolicyConstants.MEMBERSHIP_MONTHLY_FEE, now.toLocalDate(),
            now.toLocalDate().plusMonths(1).minusDays(1)));
        BillingKey key = billingKeys.getReferenceById(activeKeyId.get());
        MembershipPaymentAttempt attempt =
            attempts.saveAndFlush(MembershipPaymentAttempt.ready(payment, 1, key, now,
                MembershipBillingPeriods.firstChargeLeaseUntil(now, properties.lease(),
                    MembershipBillingPeriods.firstChargeAcceptUntil(now, properties.firstChargeAcceptWindow()))));
        log.info("체험 전환 첫 결제 접수: memberId={}, trialMembershipId={}, membershipId={}, attemptId={}", memberId,
            trialMembershipId, paidMembershipId, attempt.getId());
        return new ConversionReceipt(ConversionReceipt.Kind.CHARGE_READY, attempt.getId());
    }

    // 전환 대기 재확인(C-13 조건을 잠금 아래에서 다시): TRIAL, 미종료, 자동갱신, 해지 예약 없음, 종료 시각 도달
    private static boolean isConversionPending(BillingMembershipData trial, LocalDateTime now) {
        return trial.type() == MembershipType.TRIAL && trial.terminatedAt() == null && trial.autoRenew()
            && trial.cancelReservedAt() == null && !now.isBefore(trial.endAt());
    }
}
