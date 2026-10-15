package com.recipick.domain.payment.entity;

import com.recipick.domain.payment.entity.enums.PointUsageStatus;
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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "point_usage",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_point_usage_point_earning_id_order_id",
                columnNames = {"point_earning_id", "order_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointUsage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_earning_id")
    private PointEarning pointEarning;

    private Long orderId;

    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PointUsageStatus status;

    private LocalDateTime reservedAt;

    private LocalDateTime confirmedAt;

    @Builder
    private PointUsage(
            PointEarning pointEarning,
            Long orderId,
            Long amount,
            PointUsageStatus status,
            LocalDateTime reservedAt
    ) {
        this.pointEarning = pointEarning;
        this.orderId = orderId;
        this.amount = amount;
        this.status = status;
        this.reservedAt = reservedAt;
    }
}
