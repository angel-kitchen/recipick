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
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import lombok.AccessLevel;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "point_earning",
       indexes = @Index(name = "idx_point_earning_member_id_status", columnList = "member_id, status")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointEarning extends BaseTimeEntity {

    // 적립 확정일부터 1년. 만료일 당일까지 사용할 수 있다
    private static final int VALID_YEARS = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long memberId;

    @Column(unique = true)
    private Long orderId;

    private Long amount;

    private Long remainingAmount;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private PointEarningStatus status;

    private LocalDateTime confirmedAt;

    private LocalDate expiresOn;

    private PointEarning(Long memberId, Long orderId, Long amount) {
        this.memberId = memberId;
        this.orderId = orderId;
        this.amount = amount;
        this.remainingAmount = 0L;
        this.status = PointEarningStatus.PENDING;
    }

    public static PointEarning pending(Long memberId, Long orderId, long amount) {
        Objects.requireNonNull(memberId, "회원 ID는 필수입니다.");
        Objects.requireNonNull(orderId, "주문 ID는 필수입니다.");
        if (amount < 0) {
            throw new IllegalArgumentException("적립 예정 포인트는 음수일 수 없습니다: " + amount);
        }
        return new PointEarning(memberId, orderId, amount);
    }

    // 배송완료. 이때부터 사용할 수 있고 만료일이 정해진다.
    // 검증을 모두 마친 뒤에 값을 바꿔, 예외가 나도 PENDING 그대로 남게 한다
    public boolean complete(LocalDateTime now) {
        if (status == PointEarningStatus.COMPLETED) {
            return false;
        }
        if (status != PointEarningStatus.PENDING) {
            throw invalidTransition("적립 확정");
        }
        Objects.requireNonNull(now, "적립 확정 시각은 필수입니다.");
        this.status = PointEarningStatus.COMPLETED;
        this.confirmedAt = now;
        this.expiresOn = now.toLocalDate().plusYears(VALID_YEARS);
        this.remainingAmount = amount;
        return true;
    }

    // 배송완료 전에 주문이 끝났다. 적립 완료 건은 거래가 끝난 것이라 자동으로 취소(회수)하지 않는다
    public boolean cancel() {
        return switch (status) {
            case PENDING -> {
                this.status = PointEarningStatus.CANCELED;
                yield true;
            }
            case CANCELED -> false;
            case COMPLETED, EXPIRED -> throw invalidTransition("적립 취소");
        };
    }

    public void use(long usedAmount) {
        requireCompleted("포인트 사용");
        if (usedAmount <= 0 || usedAmount > remainingAmount) {
            throw new IllegalStateException("적립 건 잔량을 넘어 사용할 수 없습니다. earningId=" + id
                + ", remaining=" + remainingAmount + ", amount=" + usedAmount);
        }
        this.remainingAmount -= usedAmount;
    }

    // 사용했던 만큼만 되돌린다. 원래 적립액을 넘으면 같은 사용을 두 번 복원한 것이다
    public void restore(long restoredAmount) {
        requireCompleted("포인트 복원");
        if (restoredAmount <= 0 || remainingAmount + restoredAmount > amount) {
            throw new IllegalStateException("적립 건 원래 금액을 넘어 복원할 수 없습니다. earningId=" + id
                + ", amount=" + amount + ", remaining=" + remainingAmount + ", restore=" + restoredAmount);
        }
        this.remainingAmount += restoredAmount;
    }

    // 소멸한 잔량을 돌려준다. 서비스가 그 값으로 회원 잔액 차감과 EXPIRED 이력을 처리하고, 0이면 아무것도 하지 않는다
    public long expire() {
        return switch (status) {
            case COMPLETED -> {
                long expired = remainingAmount;
                this.status = PointEarningStatus.EXPIRED;
                this.remainingAmount = 0L;
                yield expired;
            }
            case EXPIRED -> 0L;
            case PENDING, CANCELED -> throw invalidTransition("적립 만료");
        };
    }

    // 사용·복원 가능 여부는 상태가 아니라 날짜로 본다. 다 써서 잔량이 0인 적립 건은 만료 처리 대상이 아니라
    // 만료일이 지나도 COMPLETED로 남을 수 있기 때문이다
    public boolean isUsableOn(LocalDate today) {
        Objects.requireNonNull(today, "기준 날짜는 필수입니다.");
        return status == PointEarningStatus.COMPLETED && !expiresOn.isBefore(today);
    }

    private void requireCompleted(String action) {
        if (status != PointEarningStatus.COMPLETED) {
            throw invalidTransition(action);
        }
    }

    private IllegalStateException invalidTransition(String action) {
        return new IllegalStateException(action + "을(를) 할 수 없는 적립 상태입니다. earningId=" + id + ", status=" + status);
    }
}
