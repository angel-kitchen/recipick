package com.recipick.domain.payment.entity;

import com.recipick.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "membership_payment",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_membership_payment_membership_id_period_start_date",
                columnNames = {"membership_id", "period_start_date"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MembershipPayment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long membershipId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_key_id")
    private BillingKey billingKey;

    @Column(unique = true)
    private String portonePaymentId;

    private Long amount;

    // TODO: 값 목록 확정 후 enum으로 교체
    @Column(length = 30)
    private String status;

    private LocalDate billingDate;

    private LocalDate periodStartDate;

    private LocalDate periodEndDate;

    private int attemptCount;

    private LocalDateTime lastAttemptAt;

    private LocalDateTime nextRetryAt;

    @Column(length = 500)
    private String failureReason;

    private LocalDateTime paidAt;

    @Builder
    private MembershipPayment(
            Long membershipId,
            BillingKey billingKey,
            String portonePaymentId,
            Long amount,
            String status,
            LocalDate billingDate,
            LocalDate periodStartDate,
            LocalDate periodEndDate
    ) {
        this.membershipId = membershipId;
        this.billingKey = billingKey;
        this.portonePaymentId = portonePaymentId;
        this.amount = amount;
        this.status = status;
        this.billingDate = billingDate;
        this.periodStartDate = periodStartDate;
        this.periodEndDate = periodEndDate;
    }
}
