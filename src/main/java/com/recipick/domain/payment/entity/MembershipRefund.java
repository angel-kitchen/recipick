package com.recipick.domain.payment.entity;

import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import com.recipick.global.entity.BaseTimeEntity;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "membership_refund")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MembershipRefund extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_payment_id")
    private MembershipPayment membershipPayment;

    private Long amount;

    private int usedDays;

    private Long usedDayFee;

    private Long penaltyAmount;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private MembershipRefundStatus status;

    @Column(length = 500)
    private String reason;

    // TODO: 값 목록 확정 후 enum으로 교체
    @Column(length = 20)
    private String requestedBy;

    @Column(unique = true)
    private String idempotencyKey;

    @Column(unique = true)
    private String portoneCancellationId;

    @Column(length = 500)
    private String failureReason;

    private LocalDateTime requestedAt;

    private LocalDateTime completedAt;

    @Builder
    private MembershipRefund(
            MembershipPayment membershipPayment,
            Long amount,
            int usedDays,
            Long usedDayFee,
            Long penaltyAmount,
            MembershipRefundStatus status,
            String reason,
            String requestedBy,
            String idempotencyKey,
            LocalDateTime requestedAt
    ) {
        this.membershipPayment = membershipPayment;
        this.amount = amount;
        this.usedDays = usedDays;
        this.usedDayFee = usedDayFee;
        this.penaltyAmount = penaltyAmount;
        this.status = status;
        this.reason = reason;
        this.requestedBy = requestedBy;
        this.idempotencyKey = idempotencyKey;
        this.requestedAt = requestedAt;
    }
}
