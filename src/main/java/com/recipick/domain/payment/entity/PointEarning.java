package com.recipick.domain.payment.entity;

import com.recipick.domain.payment.entity.enums.PointEarningStatus;
import com.recipick.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "point_earning")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointEarning extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long memberId;

    @Column(unique = true)
    private Long orderId;

    private Long amount;

    private Long remainingAmount;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PointEarningStatus status;

    private LocalDateTime confirmedAt;

    private LocalDate expiresOn;

    @Builder
    private PointEarning(Long memberId, Long orderId, Long amount, Long remainingAmount, PointEarningStatus status) {
        this.memberId = memberId;
        this.orderId = orderId;
        this.amount = amount;
        this.remainingAmount = remainingAmount;
        this.status = status;
    }
}
