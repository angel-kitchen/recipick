package com.recipick.domain.payment.entity;

import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 회차 하나에 대한 PortOne 결제 시도. 시도마다 결제 ID("mpay_" + UUID)가 하나라 PortOne 쪽 멱등 단위가 된다.
// 빌링키는 접수 때 고정되고 바꾸는 메서드가 없다(I-17) — 시도 중에 카드가 교체돼도 이 시도는 원래 카드로 끝난다.
// 2단계는 모델만 둔다. ready·dispatch·markPaid 등 전이는 4단계
@Getter
@Entity
@Table(
    name = "membership_payment_attempt",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_membership_payment_attempt_portone_payment_id",
            columnNames = "portone_payment_id"
        ),
        @UniqueConstraint(
            name = "uk_membership_payment_attempt_payment_id_attempt_no",
            columnNames = {"membership_payment_id", "attempt_no"}
        )
    },
    indexes = {
        @Index(name = "idx_membership_payment_attempt_payment_id_status", columnList = "membership_payment_id, status"),
        @Index(name = "idx_membership_payment_attempt_billing_key_id_status", columnList = "billing_key_id, status")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MembershipPaymentAttempt extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_payment_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_membership_payment_attempt_payment"))
    private MembershipPayment membershipPayment;

    @Column(nullable = false)
    private int attemptNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_key_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_membership_payment_attempt_billing_key"))
    private BillingKey billingKey;

    @Column(nullable = false)
    private String portonePaymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private MembershipPaymentAttemptStatus status;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    // 선점(DISPATCHING) 임대 만료. 지나면 복구 Sweep이 다시 본다
    @Column(nullable = false)
    private LocalDateTime leaseExpiresAt;

    private LocalDateTime dispatchedAt;

    private LocalDateTime completedAt;

    // PortOne이 확인한 결제 완료 시각(KST)
    private LocalDateTime pgPaidAt;

    @Column(length = 500)
    private String failureReason;

    private LocalDateTime opsReviewRequiredAt;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(40)")
    private MembershipOpsReviewReason opsReviewReason;

    // 결제는 됐지만 회차가 이미 끝나 돌려줘야 하는 시도(I-19). SYSTEM 환불 COMPLETED 전까지 C-17이 미정산으로 본다
    private LocalDateTime refundRequiredAt;

    @Builder
    private MembershipPaymentAttempt(
        MembershipPayment membershipPayment,
        int attemptNo,
        BillingKey billingKey,
        String portonePaymentId,
        MembershipPaymentAttemptStatus status,
        LocalDateTime requestedAt,
        LocalDateTime leaseExpiresAt,
        LocalDateTime dispatchedAt,
        LocalDateTime completedAt,
        LocalDateTime pgPaidAt,
        String failureReason,
        LocalDateTime opsReviewRequiredAt,
        MembershipOpsReviewReason opsReviewReason,
        LocalDateTime refundRequiredAt
    ) {
        this.membershipPayment = membershipPayment;
        this.attemptNo = attemptNo;
        this.billingKey = billingKey;
        this.portonePaymentId = portonePaymentId;
        this.status = status;
        this.requestedAt = requestedAt;
        this.leaseExpiresAt = leaseExpiresAt;
        this.dispatchedAt = dispatchedAt;
        this.completedAt = completedAt;
        this.pgPaidAt = pgPaidAt;
        this.failureReason = failureReason;
        this.opsReviewRequiredAt = opsReviewRequiredAt;
        this.opsReviewReason = opsReviewReason;
        this.refundRequiredAt = refundRequiredAt;
    }
}
