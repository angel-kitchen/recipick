package com.recipick.domain.payment.entity;

import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 멤버십 이용료 환불. 환불은 항상 어느 시도(PortOne 결제 ID)를 취소하는지 안다 — 회차가 아니라 시도에 NOT NULL로 묶는다.
// requestedBy는 MEMBER(즉시 해지)·SYSTEM(종료된 회차의 늦은 결제 반환)만 쓴다.
@Getter
@Entity
@Table(
    name = "membership_refund",
    indexes = {
        @Index(name = "idx_membership_refund_payment_id_status", columnList = "membership_payment_id, status"),
        @Index(
            name = "idx_membership_refund_attempt_requester_status",
            columnList = "membership_payment_attempt_id, requested_by, status"
        )
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MembershipRefund extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_payment_id")
    private MembershipPayment membershipPayment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_payment_attempt_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_membership_refund_attempt"))
    private MembershipPaymentAttempt membershipPaymentAttempt;

    private Long amount;

    private int usedDays;

    private Long usedDayFee;

    private Long penaltyAmount;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private MembershipRefundStatus status;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private RefundRequester requestedBy;

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
        MembershipPaymentAttempt membershipPaymentAttempt,
        Long amount,
        int usedDays,
        Long usedDayFee,
        Long penaltyAmount,
        MembershipRefundStatus status,
        String reason,
        RefundRequester requestedBy,
        String idempotencyKey,
        LocalDateTime requestedAt,
        LocalDateTime completedAt
    ) {
        this.membershipPayment = membershipPayment;
        this.membershipPaymentAttempt = membershipPaymentAttempt;
        this.amount = amount;
        this.usedDays = usedDays;
        this.usedDayFee = usedDayFee;
        this.penaltyAmount = penaltyAmount;
        this.status = status;
        this.reason = reason;
        this.requestedBy = requestedBy;
        this.idempotencyKey = idempotencyKey;
        this.requestedAt = requestedAt;
        this.completedAt = completedAt;
    }
}
