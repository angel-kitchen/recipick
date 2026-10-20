package com.recipick.domain.payment.entity;

import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.global.entity.BaseTimeEntity;
import com.recipick.global.exception.BusinessException;
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

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private PaymentMethod paymentMethod;

    @Column(unique = true)
    private String portonePaymentId;

    private String portoneTransactionId;

    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(500)")
    private PaymentFailureReason failureReason;

    private LocalDateTime expiresAt;

    private LocalDateTime paidAt;

    private LocalDateTime canceledAt;

    @Builder
    private Payment(
        Long orderId,
        PaymentMethod paymentMethod,
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

    public void markPaid(String portoneTransactionId, LocalDateTime paidAt) {
        if (this.status != PaymentStatus.IN_PROGRESS) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }
        this.status = PaymentStatus.PAID;
        this.portoneTransactionId = portoneTransactionId;
        this.paidAt = paidAt;
    }

    public void fail(
        PaymentFailureReason failureReason,
        String portoneTransactionId
    ) {
        if (this.status != PaymentStatus.IN_PROGRESS) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }
        this.status = PaymentStatus.FAILED;
        this.failureReason = failureReason;
        this.portoneTransactionId = portoneTransactionId;
    }

}

