package com.recipick.domain.member.service;

import com.recipick.domain.member.dto.BillingMembershipData;
import com.recipick.domain.member.dto.MembershipBillingResult;
import com.recipick.domain.member.dto.MembershipBillingTarget;
import com.recipick.domain.member.dto.MembershipConsent;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MembershipBillingService {
    private final MembershipRepository memberships;
    private final MemberRepository members;

    // 호출자는 회원을 먼저 잠그며, 이용권은 소유 회원 조건의 PK로만 잠근다.
    @Transactional(propagation = Propagation.MANDATORY)
    public BillingMembershipData getBillingMembershipForUpdate(Long memberId, Long membershipId) {
        requireWritableTransaction();
        Membership value = memberships.findByIdAndMemberIdForUpdate(membershipId, memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.ACTIVE_MEMBERSHIP_NOT_FOUND));
        return new BillingMembershipData(value.getId(), value.getMember().getId(), value.getType(), value.getSource(),
                value.getStartAt(), value.getEndAt(), value.getActivatedAt(), value.getGraceEndAt(),
                value.isAutoRenew(), value.getCancelReservedAt(), value.getTerminatedAt(), value.getBillingAnchorDate(),
                new MembershipConsent(value.getConsentedAmount(), value.getConsentTextVersion(), value.getAutopayConsentedAt()));
    }

    // 웹훅·복구는 잠금 트랜잭션에 진입하기 전에 별도 읽기 조회로 회원 ID를 찾는다.
    // 이후 회원부터 잠그고 getBillingMembershipForUpdate로 소유권과 상태를 다시 확인한다.
    public Long findMemberIdOfMembership(Long membershipId) {
        return memberships.findMemberIdOfMembership(membershipId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.ACTIVE_MEMBERSHIP_NOT_FOUND));
    }

    public List<Long> findMembershipIds(Long memberId) {
        return memberships.findIdsByMemberId(memberId);
    }

    public boolean existsBlockingMembership(Long memberId, LocalDateTime now) {
        requireTime(now);
        return memberships.existsBlocking(memberId, now);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long createPendingSubscription(Long memberId, MembershipConsent consent, LocalDateTime requestedAt) {
        requireWritableTransaction();
        requireTime(requestedAt);
        if (memberships.existsBlocking(memberId, requestedAt)) {
            throw new BusinessException(MemberErrorCode.MEMBERSHIP_ALREADY_ACTIVE);
        }
        Member member = members.findById(memberId).filter(value -> !value.isWithdrawn())
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
        return memberships.save(Membership.pendingPaid(member, MembershipSource.SUBSCRIBE,
                requestedAt, requestedAt.plusMonths(1), consent)).getId();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long createPendingConversion(Long memberId, Long trialMembershipId, LocalDateTime now) {
        requireWritableTransaction();
        requireTime(now);
        // 호출자는 앞서 같은 회원과 체험 이용권을 잠그고 전환 가능 여부를 재확인한다.
        Membership trial = findMembership(trialMembershipId);
        if (!trial.getMember().getId().equals(memberId) || !trial.isConversionPending(now)) {
            throw new IllegalStateException("잠근 체험 이용권이 전환 대기 상태여야 합니다.");
        }
        MembershipConsent consent = new MembershipConsent(trial.getConsentedAmount(),
                trial.getConsentTextVersion(), trial.getAutopayConsentedAt());
        Membership pending = Membership.pendingPaid(trial.getMember(), MembershipSource.TRIAL_CONVERT,
                now, now.plusMonths(1), consent);
        trial.handOverConversion(now);
        return memberships.save(pending).getId();
    }

    public List<MembershipBillingTarget> findTrialConversionTargets(LocalDateTime now, Long afterMembershipId, int limit) {
        return memberships.findTrialConversionTargets(requireTime(now), cursor(afterMembershipId), page(limit));
    }

    public List<MembershipBillingTarget> findRenewalTargets(LocalDateTime now, Long afterMembershipId, int limit) {
        return memberships.findRenewalTargets(requireTime(now), cursor(afterMembershipId), page(limit));
    }

    // 결과 반영 전 회원·이용권·회차·시도 잠금과 현재 회차 검증은 결제 호출자가 수행한다.
    // 이미 잠근 이용권을 같은 영속성 컨텍스트에서 얻고 결과 기록과 함께 커밋한다.
    @Transactional(propagation = Propagation.MANDATORY)
    public MembershipBillingResult applyFirstPaymentPaid(Long membershipId, LocalDateTime pgPaidAt,
            LocalDateTime endAt, LocalDateTime graceEndAt) {
        requireWritableTransaction();
        return findMembership(membershipId).activateAfterFirstPayment(pgPaidAt, endAt, graceEndAt);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public MembershipBillingResult applyFirstPaymentFailed(Long membershipId, LocalDateTime failedAt) {
        requireWritableTransaction();
        return findMembership(membershipId).closeUnpaid(failedAt);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public MembershipBillingResult applyRenewalPaid(Long membershipId, LocalDateTime newEndAt, LocalDateTime newGraceEndAt) {
        requireWritableTransaction();
        return findMembership(membershipId).extendAfterRenewal(newEndAt, newGraceEndAt);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public MembershipBillingResult stopRenewal(Long membershipId) {
        requireWritableTransaction();
        return findMembership(membershipId).stopRenewal();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public MembershipBillingResult closeTrialConversion(Long membershipId, LocalDateTime now) {
        requireWritableTransaction();
        return findMembership(membershipId).closeConversion(now);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public MembershipBillingResult terminateAfterRefund(Long membershipId, LocalDateTime completedAt) {
        requireWritableTransaction();
        Membership value = findMembership(membershipId);
        boolean alreadyTerminated = value.getTerminatedAt() != null;
        value.terminateAfterRefund(completedAt);
        return alreadyTerminated ? MembershipBillingResult.ALREADY_APPLIED : MembershipBillingResult.TERMINATED;
    }

    private Membership findMembership(Long membershipId) {
        return memberships.findById(membershipId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.ACTIVE_MEMBERSHIP_NOT_FOUND));
    }

    private static void requireWritableTransaction() {
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("이용권 결제 반영은 쓰기 가능한 바깥 트랜잭션이 필요합니다.");
        }
    }

    private static LocalDateTime requireTime(LocalDateTime at) {
        if (at == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return at;
    }

    private static long cursor(Long afterId) {
        if (afterId != null && afterId < 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return afterId == null ? 0 : afterId;
    }

    private static PageRequest page(int limit) {
        if (limit <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return PageRequest.of(0, limit);
    }
}
