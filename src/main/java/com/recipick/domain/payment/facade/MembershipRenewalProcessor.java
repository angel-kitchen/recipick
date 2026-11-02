package com.recipick.domain.payment.facade;

import com.recipick.domain.member.dto.BillingMembershipData;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MembershipBillingService;
import com.recipick.domain.payment.config.MembershipBillingGate;
import com.recipick.domain.payment.config.MembershipBillingProperties;
import com.recipick.domain.payment.entity.MembershipPayment;
import com.recipick.domain.payment.entity.MembershipPaymentAttempt;
import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.facade.MembershipRenewalRules.Mode;
import com.recipick.domain.payment.repository.BillingKeyRepository;
import com.recipick.domain.payment.repository.MembershipPaymentAttemptRepository;
import com.recipick.domain.payment.repository.MembershipPaymentRepository;
import com.recipick.domain.payment.service.MembershipBillingPeriods;
import com.recipick.domain.payment.service.MembershipBillingPeriods.RenewalPeriod;
import com.recipick.domain.payment.service.MembershipChargeOutcome;
import com.recipick.domain.payment.service.MembershipPaymentQueryService;
import com.recipick.domain.payment.service.MembershipRenewalDecision;
import com.recipick.domain.payment.service.MembershipRenewalDecision.Kind;
import com.recipick.global.common.PolicyConstants;
import com.recipick.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 갱신 접수·기존 회차 재개·정리의 트랜잭션. 첫 DB 작업은 회원 잠금이고 이용권(C-7) → 회차 PK → 시도 PK들(오름차순) 순으로 잡은 뒤
// 시각을 읽는다 — 잠금을 기다리는 사이 마감·기간 종료를 넘겼으면 그 시각으로 판정한다. PG는 부르지 않는다(커밋 뒤 Facade가 실행기로).
// accept: C-2 신규 후보. 이번 기간 회차가 있으면 그 회차를 이어 가고, 없을 때만 새 회차를 만든다.
// resumeExisting: 조회해 둔 기존 회차만. 이용권의 현재 기간으로 다른 회차를 찾거나 새 회차를 만들지 않는다.
// settleExisting: 기존 회차의 만료 READY 취소·해지·마감·한도 종료·대기 정규화만. 새 시도·새 회차·실행 없음(플래그 무관).
// settleOverdue: 결제 마감이 지났는데 회차가 없는 이용권을 FAILED 회차 1건 + 자동갱신 끔으로 닫는다(시도·POST 없음, 플래그 무관).
// 신규 접수(accept·resumeExisting)는 탈퇴 회원을 거절하는 회원 잠금을, 정리(settle*)는 탈퇴 회원도 잠그는 정산 잠금을 쓴다
@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipRenewalProcessor {

    private final MemberService memberService;
    private final MembershipBillingService membershipBillingService;
    private final MembershipPaymentQueryService membershipPaymentQueryService;
    private final MembershipPaymentRepository payments;
    private final MembershipPaymentAttemptRepository attempts;
    private final BillingKeyRepository billingKeys;
    private final MembershipRenewalRules rules;
    private final MembershipBillingGate gate;
    private final MembershipBillingProperties properties;
    private final Clock clock;

    private static final String REASON_MISSED_NO_ATTEMPT = "RENEWAL_MISSED_NO_ATTEMPT";

    public record CloseResult(MembershipChargeOutcome outcome) {
    }

    @Transactional
    public MembershipRenewalDecision accept(Long memberId, Long membershipId) {
        memberService.lockActiveMember(memberId);
        BillingMembershipData membership = membershipBillingService.getBillingMembershipForUpdate(memberId,
            membershipId);
        if (!MembershipRenewalRules.renewable(membership)) {
            return MembershipRenewalDecision.of(Kind.SKIPPED, null);
        }
        RenewalPeriod period;
        try {
            period = MembershipBillingPeriods.renewalPeriod(membership.billingAnchorDate(), membership.startAt(),
                membership.endAt());
        } catch (IllegalStateException e) {
            log.error("이용권 기간이 기준일과 맞지 않아 갱신을 접수하지 않음(운영 확인): membershipId={}", membershipId);
            return MembershipRenewalDecision.of(Kind.SKIPPED, null);
        }
        // 이번 기간 회차를 C-17보다 먼저 찾는다 — 자기 재시도 대기 회차 때문에 스스로 막히지 않게
        Optional<Long> existingId = payments.findIdByMembershipIdAndPeriodStartDate(membershipId,
            period.periodStart());
        if (existingId.isPresent()) {
            return continueLocked(memberId, membership, existingId.get(), Mode.CHARGE);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (!membership.autoRenew() || membership.cancelReservedAt() != null
            || now.isBefore(period.previousEndAt()) || !now.isBefore(period.graceDeadlineAt())) {
            return MembershipRenewalDecision.of(Kind.SKIPPED, null);
        }
        if (!MembershipRenewalRules.consentValid(membership)) {
            log.error("갱신 동의 금액·문구가 청구 조건과 달라 접수하지 않음(운영 확인): membershipId={}", membershipId);
            return MembershipRenewalDecision.of(Kind.SKIPPED, null);
        }
        if (!gate.newChargeEnabled()) {
            return MembershipRenewalDecision.of(Kind.BLOCKED, null);
        }
        if (membershipPaymentQueryService.existsUnresolved(membershipBillingService.findMembershipIds(memberId))) {
            log.info("미정산 결제가 있어 갱신 접수를 보류: memberId={}, membershipId={}", memberId, membershipId);
            return MembershipRenewalDecision.of(Kind.BLOCKED, null);
        }
        if (membershipBillingService.existsConflictingMembershipForBilling(memberId, membershipId, now)) {
            log.error("다른 이용권과 충돌해 갱신을 접수하지 않음(운영 확인): memberId={}, membershipId={}", memberId, membershipId);
            return MembershipRenewalDecision.of(Kind.SKIPPED, null);
        }
        // 조회 예외는 잡지 않는다 — 카드 없음으로 단정하지 않고 롤백 뒤 다음 실행에서 다시 본다
        Optional<Long> activeKeyId = billingKeys.findIdByMemberIdAndStatus(memberId, BillingKeyStatus.ACTIVE);
        MembershipPayment payment = MembershipPayment.pendingRenewal(membershipId,
            PolicyConstants.MEMBERSHIP_MONTHLY_FEE, period);
        if (activeKeyId.isEmpty()) {
            payment.waitForPaymentMethod();
            payments.saveAndFlush(payment);
            log.info("갱신할 ACTIVE 카드 없음 → 카드 대기 회차: memberId={}, membershipPaymentId={}", memberId,
                payment.getId());
            return MembershipRenewalDecision.of(Kind.WAITING_PAYMENT_METHOD, payment.getId());
        }
        payments.saveAndFlush(payment);
        MembershipPaymentAttempt attempt = attempts.saveAndFlush(MembershipPaymentAttempt.ready(payment, 1,
            billingKeys.getReferenceById(activeKeyId.get()), now,
            MembershipBillingPeriods.leaseUntil(now, properties.lease(), period.graceDeadlineAt())));
        log.info("갱신 접수: memberId={}, membershipPaymentId={}, attemptId={}", memberId, payment.getId(),
            attempt.getId());
        return MembershipRenewalDecision.chargeReady(payment.getId(), attempt.getId());
    }

    @Transactional
    public MembershipRenewalDecision resumeExisting(Long memberId, Long membershipId, Long membershipPaymentId) {
        memberService.lockActiveMember(memberId);
        BillingMembershipData membership = membershipBillingService.getBillingMembershipForUpdate(memberId,
            membershipId);
        return continueLocked(memberId, membership, membershipPaymentId, Mode.CHARGE);
    }

    @Transactional
    public CloseResult settleExisting(Long memberId, Long membershipId, Long membershipPaymentId) {
        memberService.lockMemberForSettlement(memberId);
        BillingMembershipData membership = membershipBillingService.getBillingMembershipForUpdate(memberId,
            membershipId);
        return new CloseResult(settleOutcome(continueLocked(memberId, membership, membershipPaymentId, Mode.SETTLE)));
    }

    // 결제 마감이 지났는데 자동갱신이 켜진 이용권. 이번 기간 회차가 있으면 기존 정리로 이어 가고, 회차가 없으면(접수가 한 번도
    // 되지 않음) 미정산이 없을 때만 FAILED 회차 1건 + 자동갱신 끔으로 닫는다. 다른 이용권 충돌이 있어도 같다. 시도·POST·소급 청구 없음 —
    // 몇 달을 놓쳤어도 이용권 종료 시각이 그대로라 첫 놓친 달 하나만 만들고, 끈 뒤에는 후보에서 빠진다
    @Transactional
    public CloseResult settleOverdue(Long memberId, Long membershipId) {
        memberService.lockMemberForSettlement(memberId);
        BillingMembershipData membership = membershipBillingService.getBillingMembershipForUpdate(memberId,
            membershipId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!MembershipRenewalRules.renewable(membership) || !membership.autoRenew()
            || membership.cancelReservedAt() != null) {
            return new CloseResult(MembershipChargeOutcome.IN_PROGRESS);
        }
        RenewalPeriod period;
        try {
            period = MembershipBillingPeriods.renewalPeriod(membership.billingAnchorDate(), membership.startAt(),
                membership.endAt());
        } catch (IllegalStateException e) {
            log.error("이용권 기간이 기준일과 맞지 않아 마감 누락을 정리하지 않음(운영 확인): membershipId={}", membershipId);
            return new CloseResult(MembershipChargeOutcome.RECONCILE_HALTED);
        }
        if (now.isBefore(period.graceDeadlineAt())) {
            return new CloseResult(MembershipChargeOutcome.IN_PROGRESS);
        }
        Optional<Long> existingId = payments.findIdByMembershipIdAndPeriodStartDate(membershipId,
            period.periodStart());
        if (existingId.isPresent()) {
            MembershipRenewalDecision decision = continueLocked(memberId, membership, existingId.get(), Mode.SETTLE);
            if (decision.kind() == Kind.ALREADY_RESOLVED) {
                // 이번 기간 회차가 이미 확정인데 이용권은 연장되지도 자동갱신이 꺼지지도 않았다
                log.error("확정된 갱신 회차와 이용권 상태가 맞지 않음(운영 확인): membershipId={}, membershipPaymentId={}",
                    membershipId, existingId.get());
                return new CloseResult(MembershipChargeOutcome.RECONCILE_HALTED);
            }
            return new CloseResult(settleOutcome(decision));
        }
        if (membershipPaymentQueryService.existsUnresolved(membershipBillingService.findMembershipIds(memberId))) {
            log.info("미정산 결제가 있어 마감 누락 정리를 다음 주기로 미룸: memberId={}, membershipId={}", memberId, membershipId);
            return new CloseResult(MembershipChargeOutcome.IN_PROGRESS);
        }
        MembershipPayment payment = MembershipPayment.pendingRenewal(membershipId,
            PolicyConstants.MEMBERSHIP_MONTHLY_FEE, period);
        payment.markFailed(REASON_MISSED_NO_ATTEMPT);
        payments.saveAndFlush(payment);
        rules.stopRenewal(membershipId);
        log.warn("결제 마감까지 갱신 접수가 없어 회차를 실패로 닫고 자동갱신을 끔: membershipId={}, membershipPaymentId={}",
            membershipId, payment.getId());
        return new CloseResult(MembershipChargeOutcome.RENEWAL_STOPPED);
    }

    private static MembershipChargeOutcome settleOutcome(MembershipRenewalDecision decision) {
        return switch (decision.kind()) {
            case ALREADY_RESOLVED -> MembershipChargeOutcome.ALREADY_APPLIED;
            case CLOSED -> MembershipChargeOutcome.RENEWAL_STOPPED;
            case PG_PENDING -> MembershipChargeOutcome.STILL_UNKNOWN;
            case HALTED -> MembershipChargeOutcome.RECONCILE_HALTED;
            default -> MembershipChargeOutcome.IN_PROGRESS;
        };
    }

    // 회원·이용권 잠금 뒤. 회차 PK → 시도 PK들 순으로 잠그고 그 뒤의 시각으로 판정한다
    private MembershipRenewalDecision continueLocked(Long memberId, BillingMembershipData membership,
                                                     Long membershipPaymentId, Mode mode) {
        MembershipPayment payment = payments.findByIdForUpdate(membershipPaymentId)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.MEMBERSHIP_PAYMENT_NOT_FOUND));
        if (!payment.getMembershipId().equals(membership.membershipId()) || !payment.isRenewal()) {
            throw new IllegalStateException("갱신 회차와 이용권 연결이 맞지 않음: membershipPaymentId="
                + membershipPaymentId);
        }
        List<MembershipPaymentAttempt> locked = rules.lockAttempts(payment);
        LocalDateTime now = LocalDateTime.now(clock);
        return rules.continueExisting(memberId, membership, payment, locked, now, mode);
    }
}
