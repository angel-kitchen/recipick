package com.recipick.domain.payment.entity;

import com.recipick.domain.payment.entity.enums.PointEarningStatus;
import com.recipick.domain.payment.entity.enums.PointHistoryType;
import com.recipick.domain.payment.entity.enums.PointUsageStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "point_history",
    indexes = {
        @Index(name = "idx_point_history_member_created", columnList = "member_id, created_at"),
        @Index(name = "idx_point_history_member_order_created", columnList = "member_id, order_id, created_at")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_earning_id")
    private PointEarning pointEarning;

    private Long memberId;

    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(30)")
    private PointHistoryType type;

    private Long amount;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private PointHistory(PointEarning pointEarning, Long orderId, PointHistoryType type, long amount) {
        Objects.requireNonNull(pointEarning, "적립 건은 필수입니다.");
        Objects.requireNonNull(type, "이력 종류는 필수입니다.");
        if (amount <= 0) {
            throw new IllegalArgumentException("포인트 이력 금액은 0보다 커야 합니다: " + amount);
        }
        this.pointEarning = pointEarning;
        this.memberId = pointEarning.getMemberId();
        this.orderId = orderId;
        this.type = type;
        this.amount = amount;
    }

    public static PointHistory earnPending(PointEarning earning) {
        return ofEarning(earning, PointEarningStatus.PENDING, PointHistoryType.EARN_PENDING);
    }

    public static PointHistory earnCompleted(PointEarning earning) {
        return ofEarning(earning, PointEarningStatus.COMPLETED, PointHistoryType.EARN_COMPLETED);
    }

    public static PointHistory earnCanceled(PointEarning earning) {
        return ofEarning(earning, PointEarningStatus.CANCELED, PointHistoryType.EARN_CANCELED);
    }

    public static PointHistory useReserved(PointUsage usage) {
        return ofUsage(usage, PointUsageStatus.RESERVED, PointHistoryType.USE_RESERVED);
    }

    public static PointHistory useConfirmed(PointUsage usage) {
        return ofUsage(usage, PointUsageStatus.CONFIRMED, PointHistoryType.USE_CONFIRMED);
    }

    public static PointHistory restored(PointUsage usage) {
        return ofUsage(usage, PointUsageStatus.RESTORED, PointHistoryType.RESTORED);
    }

    // 지연 만료·만료 배치는 주문이 없어 orderId가 null이고, 만료일이 지난 포인트를 복원한 직후의 소멸은 그 주문을 남긴다.
    // 복원 직후 소멸은 적립 건이 이미 EXPIRED였을 수도 있어 이번 호출에서 상태가 바뀌었는지는 보지 않는다
    public static PointHistory expired(PointEarning earning, Long orderId, long amount) {
        requireEarningStatus(earning, PointEarningStatus.EXPIRED, PointHistoryType.EXPIRED);
        return new PointHistory(earning, orderId, PointHistoryType.EXPIRED, amount);
    }

    // 탈퇴 소멸은 주문이 없어 orderId가 null이고, 이미 탈퇴 소멸된 적립 건으로 사용분이 복원된 직후의 소멸은 그 주문을 남긴다.
    // 자연 만료(EXPIRED)와 구분해 탈퇴로 사라진 포인트만 따로 집계할 수 있게 한다
    public static PointHistory withdrawalForfeited(PointEarning earning, Long orderId, long amount) {
        requireEarningStatus(earning, PointEarningStatus.FORFEITED, PointHistoryType.WITHDRAWAL_FORFEITED);
        return new PointHistory(earning, orderId, PointHistoryType.WITHDRAWAL_FORFEITED, amount);
    }

    private static PointHistory ofEarning(PointEarning earning, PointEarningStatus expected, PointHistoryType type) {
        requireEarningStatus(earning, expected, type);
        return new PointHistory(earning, earning.getOrderId(), type, earning.getAmount());
    }

    private static PointHistory ofUsage(PointUsage usage, PointUsageStatus expected, PointHistoryType type) {
        Objects.requireNonNull(usage, "포인트 사용 내역은 필수입니다.");
        if (usage.getStatus() != expected) {
            throw new IllegalStateException(type + " 이력은 " + expected + " 사용 내역에만 남길 수 있습니다. usageId="
                + usage.getId() + ", status=" + usage.getStatus());
        }
        return new PointHistory(usage.getPointEarning(), usage.getOrderId(), type, usage.getAmount());
    }

    private static void requireEarningStatus(PointEarning earning, PointEarningStatus expected, PointHistoryType type) {
        Objects.requireNonNull(earning, "적립 건은 필수입니다.");
        if (earning.getStatus() != expected) {
            throw new IllegalStateException(type + " 이력은 " + expected + " 적립 건에만 남길 수 있습니다. earningId="
                + earning.getId() + ", status=" + earning.getStatus());
        }
    }
}
