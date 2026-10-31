package com.recipick.domain.member.entity;

import com.recipick.domain.member.dto.MembershipBillingResult;
import com.recipick.domain.member.dto.MembershipConsent;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipStatus;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.global.entity.BaseTimeEntity;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "membership")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Membership extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by_admin_id")
    private Admin grantedByAdmin;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private MembershipType type;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private MembershipSource source;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    // 실제 활성화된 시각부터 혜택을 제공한다.
    private LocalDateTime activatedAt;

    private LocalDateTime graceEndAt;

    private boolean autoRenew;

    private LocalDate billingAnchorDate;

    private Long consentedAmount;

    @Column(length = 20)
    private String consentTextVersion;

    private LocalDateTime autopayConsentedAt;

    private LocalDateTime cancelReservedAt;

    private LocalDateTime terminatedAt;

    @Builder
    private Membership(
            Member member,
            Admin grantedByAdmin,
            MembershipType type,
            MembershipSource source,
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDateTime activatedAt,
            boolean autoRenew,
            LocalDate billingAnchorDate,
            Long consentedAmount,
            String consentTextVersion,
            LocalDateTime autopayConsentedAt
    ) {
        if (type == null || source == null || startAt == null || endAt == null || !startAt.isBefore(endAt)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if ((source == MembershipSource.TRIAL || source == MembershipSource.ADMIN_GRANT)
                && !startAt.equals(activatedAt)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        this.member = member;
        this.grantedByAdmin = grantedByAdmin;
        this.type = type;
        this.source = source;
        this.startAt = startAt;
        this.endAt = endAt;
        this.activatedAt = activatedAt;
        this.autoRenew = autoRenew;
        this.billingAnchorDate = billingAnchorDate;
        this.consentedAmount = consentedAmount;
        this.consentTextVersion = consentTextVersion;
        this.autopayConsentedAt = autopayConsentedAt;
    }

    public static Membership pendingPaid(Member member, MembershipSource source,
            LocalDateTime provisionalStartAt, LocalDateTime provisionalEndAt, MembershipConsent consent) {
        if (member == null || (source != MembershipSource.SUBSCRIBE && source != MembershipSource.TRIAL_CONVERT)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (consent == null || consent.consentedAmount() == null || consent.consentedAmount() <= 0
                || consent.consentTextVersion() == null || consent.consentTextVersion().isBlank()
                || consent.consentTextVersion().length() > 20 || consent.autopayConsentedAt() == null) {
            throw new BusinessException(MemberErrorCode.AUTOPAY_CONSENT_REQUIRED);
        }
        if (provisionalStartAt == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return Membership.builder().member(member).type(MembershipType.PAID).source(source)
                .startAt(provisionalStartAt).endAt(provisionalEndAt).autoRenew(true)
                .billingAnchorDate(provisionalStartAt.toLocalDate()).consentedAmount(consent.consentedAmount())
                .consentTextVersion(consent.consentTextVersion()).autopayConsentedAt(consent.autopayConsentedAt()).build();
    }

    public MembershipBillingResult activateAfterFirstPayment(LocalDateTime pgPaidAt,
            LocalDateTime newEndAt, LocalDateTime newGraceEndAt) {
        if (type != MembershipType.PAID || terminatedAt != null) {
            return MembershipBillingResult.CONFLICT;
        }
        if (activatedAt != null) {
            return MembershipBillingResult.ALREADY_APPLIED;
        }
        if (!isPaymentPending()) {
            return MembershipBillingResult.CONFLICT;
        }
        validateRenewalGrace(newGraceEndAt);
        validatePeriod(pgPaidAt, newEndAt, newGraceEndAt);
        startAt = pgPaidAt;
        activatedAt = pgPaidAt;
        billingAnchorDate = pgPaidAt.toLocalDate();
        endAt = newEndAt;
        graceEndAt = cancelReservedAt == null ? newGraceEndAt : null;
        return MembershipBillingResult.ACTIVATED;
    }

    public MembershipBillingResult closeUnpaid(LocalDateTime at) {
        if (type != MembershipType.PAID || activatedAt != null) {
            return MembershipBillingResult.CONFLICT;
        }
        if (terminatedAt != null) {
            return MembershipBillingResult.ALREADY_APPLIED;
        }
        if (at == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        terminatedAt = at;
        autoRenew = false;
        return MembershipBillingResult.CLOSED_UNPAID;
    }

    public MembershipBillingResult extendAfterRenewal(LocalDateTime newEndAt, LocalDateTime newGraceEndAt) {
        if (type != MembershipType.PAID || activatedAt == null || terminatedAt != null) {
            return MembershipBillingResult.CONFLICT;
        }
        if (newEndAt == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (!endAt.isBefore(newEndAt)) {
            return MembershipBillingResult.ALREADY_APPLIED;
        }
        validateRenewalGrace(newGraceEndAt);
        validatePeriod(startAt, newEndAt, newGraceEndAt);
        endAt = newEndAt;
        graceEndAt = cancelReservedAt == null ? newGraceEndAt : null;
        return MembershipBillingResult.EXTENDED;
    }

    public MembershipBillingResult stopRenewal() {
        if (type != MembershipType.PAID || activatedAt == null) {
            return MembershipBillingResult.CONFLICT;
        }
        if (!autoRenew) {
            return MembershipBillingResult.ALREADY_APPLIED;
        }
        autoRenew = false;
        return MembershipBillingResult.RENEWAL_STOPPED;
    }

    public MembershipBillingResult handOverConversion(LocalDateTime now) {
        if (now == null || !isConversionPending(now)) {
            return MembershipBillingResult.CONFLICT;
        }
        autoRenew = false;
        return MembershipBillingResult.CONVERSION_CLOSED;
    }

    public MembershipBillingResult closeConversion(LocalDateTime now) {
        if (now == null || type != MembershipType.TRIAL || terminatedAt != null || now.isBefore(endAt)) {
            return MembershipBillingResult.CONFLICT;
        }
        if (!autoRenew) {
            return MembershipBillingResult.ALREADY_APPLIED;
        }
        autoRenew = false;
        return MembershipBillingResult.CONVERSION_CLOSED;
    }

    private void validateRenewalGrace(LocalDateTime newGraceEndAt) {
        if (autoRenew && cancelReservedAt == null && newGraceEndAt == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    private static void validatePeriod(LocalDateTime start, LocalDateTime end, LocalDateTime graceEnd) {
        if (start == null || end == null || !start.isBefore(end)
                || (graceEnd != null && graceEnd.isBefore(end))) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    public boolean isActive(LocalDateTime now) {
        return terminatedAt == null && activatedAt != null && !now.isBefore(activatedAt)
                && !now.isBefore(startAt) && now.isBefore(effectiveEnd());
    }

    public LocalDateTime effectiveEnd() {
        return graceEndAt != null ? graceEndAt : endAt;
    }

    public boolean isPaymentPending() {
        return type == MembershipType.PAID && activatedAt == null && terminatedAt == null;
    }

    public boolean isConversionPending(LocalDateTime now) {
        return type == MembershipType.TRIAL && terminatedAt == null && autoRenew
                && cancelReservedAt == null && !now.isBefore(endAt);
    }

    public Optional<MembershipStatus> getStatus(LocalDateTime now) {
        if (terminatedAt != null) {
            return Optional.of(MembershipStatus.TERMINATED);
        }
        if (isPaymentPending()) {
            return Optional.of(MembershipStatus.PAYMENT_PENDING);
        }
        if (isConversionPending(now)) {
            return Optional.of(MembershipStatus.CONVERSION_PENDING);
        }
        if (!now.isBefore(effectiveEnd())) {
            return Optional.of(MembershipStatus.EXPIRED);
        }
        if (now.isBefore(startAt)) {
            // 미시작 이용권을 ACTIVE로 표시하지 않고, 미정인 공개 상태값도 추가하지 않는다.
            return Optional.empty();
        }
        MembershipStatus status = cancelReservedAt != null
                ? MembershipStatus.CANCEL_RESERVED
                : MembershipStatus.ACTIVE;
        return Optional.of(status);
    }

    public void reserveCancellation(LocalDateTime now) {
        if (!isActive(now) && !isPaymentPending() && !isConversionPending(now)) {
            throw new BusinessException(MemberErrorCode.ACTIVE_MEMBERSHIP_NOT_FOUND);
        }
        if (source == MembershipSource.ADMIN_GRANT) {
            throw new BusinessException(MemberErrorCode.NOT_CANCELABLE);
        }
        if (cancelReservedAt != null) {
            return;
        }
        autoRenew = false;
        cancelReservedAt = now;
        graceEndAt = null;
    }

    // 환불 완료 여부와 요청 멱등성은 호출하는 Service가 확인한다.
    public void terminateAfterRefund(LocalDateTime completedAt) {
        if (type != MembershipType.PAID || source == MembershipSource.ADMIN_GRANT || activatedAt == null) {
            throw new BusinessException(MemberErrorCode.NOT_TERMINABLE);
        }
        if (terminatedAt != null) {
            return;
        }
        if (completedAt == null || completedAt.isBefore(startAt)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        // 결과 불명이 기간 만료 뒤에 확정돼도 환불 완료 기록은 반영해야 한다.
        terminatedAt = completedAt;
        autoRenew = false;
        graceEndAt = null;
    }
}
