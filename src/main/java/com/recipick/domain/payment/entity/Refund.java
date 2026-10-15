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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "refund")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    private Long amount;

    @Column(length = 500)
    private String reason;

    // TODO: 값 목록 확정 후 enum으로 교체
    @Column(length = 20)
    private String status;

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
    private Refund(
            Payment payment,
            Long amount,
            String reason,
            String status,
            String requestedBy,
            String idempotencyKey,
            LocalDateTime requestedAt
    ) {
        this.payment = payment;
        this.amount = amount;
        this.reason = reason;
        this.status = status;
        this.requestedBy = requestedBy;
        this.idempotencyKey = idempotencyKey;
        this.requestedAt = requestedAt;
    }
}
