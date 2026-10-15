package com.recipick.domain.payment.entity;

import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "payment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private Long orderId;

    // TODO: 값 목록 확정 후 enum으로 교체
    @Column(length = 20)
    private String paymentMethod;

    @Column(unique = true)
    private String portonePaymentId;

    private String portoneTransactionId;

    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PaymentStatus status;

    @Column(length = 500)
    private String failureReason;

    private LocalDateTime expiresAt;

    private LocalDateTime paidAt;

    private LocalDateTime canceledAt;

    @Builder
    private Payment(
            Long orderId,
            String paymentMethod,
            String portonePaymentId,
            Long amount,
            PaymentStatus status,
            LocalDateTime expiresAt
    ) {
        this.orderId = orderId;
        this.paymentMethod = paymentMethod;
        this.portonePaymentId = portonePaymentId;
        this.amount = amount;
        this.status = status;
        this.expiresAt = expiresAt;
    }
}
