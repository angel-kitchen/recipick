package com.recipick.domain.payment.entity;

import com.recipick.domain.payment.entity.enums.PointEarningStatus;
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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;

import lombok.AccessLevel;

import lombok.Getter;
import lombok.NoArgsConstructor;


@Getter
@Entity
@Table(
        name = "point_usage",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_point_usage_point_earning_id_order_id",
                columnNames = {"point_earning_id", "order_id"}
        ),
    indexes = @Index(name = "idx_point_usage_order_id", columnList = "order_id")
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
    @Column(columnDefinition = "varchar(20)")
    private PointUsageStatus status;

    private LocalDateTime reservedAt;

    private LocalDateTime confirmedAt;

    private PointUsage(PointEarning pointEarning, Long orderId, Long amount, LocalDateTime reservedAt) {
        this.pointEarning = pointEarning;
        this.orderId = orderId;
        this.amount = amount;
        this.status = PointUsageStatus.RESERVED;
        this.reservedAt = reservedAt;
    }

    // 확정된 적립 건에만 사용을 매단다. 만료일 판단은 기준 날짜를 아는 서비스가 한다
    public static PointUsage reserve(PointEarning pointEarning, Long orderId, long amount, LocalDateTime now) {
        Objects.requireNonNull(pointEarning, "적립 건은 필수입니다.");
        Objects.requireNonNull(orderId, "주문 ID는 필수입니다.");
        Objects.requireNonNull(now, "예약 시각은 필수입니다.");
        if (pointEarning.getStatus() != PointEarningStatus.COMPLETED) {
            throw new IllegalStateException("확정된 적립 건만 사용할 수 있습니다. earningId=" + pointEarning.getId()
                + ", status=" + pointEarning.getStatus());
    }
        if (amount <= 0) {
            throw new IllegalArgumentException("사용 예약 포인트는 0보다 커야 합니다: " + amount);
        }
        return new PointUsage(pointEarning, orderId, amount, now);
    }

    // 결제 성공. 잔액은 예약 때 이미 줄었으므로 상태만 바꾼다.
    // 확정 시각이 없는 CONFIRMED 행이 생기지 않도록 실제 전이 때만 시각을 요구하고, 재호출은 시각 없이도 no-op이다
    public boolean confirm(LocalDateTime now) {
        return switch (status) {
            case RESERVED -> {
                Objects.requireNonNull(now, "확정 시각은 필수입니다.");
                this.status = PointUsageStatus.CONFIRMED;
                this.confirmedAt = now;
                yield true;
            }
            case CONFIRMED -> false;
            case RESTORED -> throw new IllegalStateException("복원된 포인트 사용은 확정할 수 없습니다. usageId=" + id);
        };
    }

    // 주문이 종료돼 사용을 되돌린다. 결제 성공 뒤의 환불도 같은 전이이고,
    // 한때 확정됐다는 사실을 남기려고 confirmedAt은 지우지 않는다
    public boolean restore() {
        if (status == PointUsageStatus.RESTORED) {
            return false;
        }
        this.status = PointUsageStatus.RESTORED;
        return true;
    }
}
