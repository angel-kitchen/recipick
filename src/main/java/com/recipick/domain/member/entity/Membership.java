package com.recipick.domain.member.entity;

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
            boolean autoRenew,
            LocalDate billingAnchorDate,
            Long consentedAmount,
            String consentTextVersion,
            LocalDateTime autopayConsentedAt
    ) {
        if (type == null || source == null || startAt == null || endAt == null || !startAt.isBefore(endAt)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        this.member = member;
        this.grantedByAdmin = grantedByAdmin;
        this.type = type;
        this.source = source;
        this.startAt = startAt;
        this.endAt = endAt;
        this.autoRenew = autoRenew;
        this.billingAnchorDate = billingAnchorDate;
        this.consentedAmount = consentedAmount;
        this.consentTextVersion = consentTextVersion;
        this.autopayConsentedAt = autopayConsentedAt;
    }

    public boolean isActive(LocalDateTime now) {
        return terminatedAt == null && !now.isBefore(startAt) && now.isBefore(endAt);
    }

    public Optional<MembershipStatus> getStatus(LocalDateTime now) {
        if (terminatedAt != null) {
            return Optional.of(MembershipStatus.TERMINATED);
        }
        if (!now.isBefore(endAt)) {
            return Optional.of(MembershipStatus.EXPIRED);
        }
        if (now.isBefore(startAt)) {
            // 미시작 이용권을 ACTIVE로 표시하지 않고, 미정인 공개 상태값도 추가하지 않는다.
            return Optional.empty();
        }
        return Optional.of(cancelReservedAt != null ? MembershipStatus.CANCEL_RESERVED : MembershipStatus.ACTIVE);
    }

    public void reserveCancellation(LocalDateTime now) {
        if (!isActive(now)) {
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
    }

    // 환불 완료 여부와 요청 멱등성은 호출하는 Service가 확인한다.
    public void terminateAfterRefund(LocalDateTime completedAt) {
        if (type != MembershipType.PAID || source == MembershipSource.ADMIN_GRANT) {
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
    }
}
