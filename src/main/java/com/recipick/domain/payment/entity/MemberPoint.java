package com.recipick.domain.payment.entity;

import com.recipick.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "member_point")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberPoint extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private Long memberId;

    private Long balance;

    @Builder
    private MemberPoint(Long memberId, Long balance) {
        if (memberId == null) {
            throw new IllegalArgumentException("회원 ID는 필수입니다.");
        }
        if (balance == null || balance < 0) {
            throw new IllegalArgumentException("포인트 잔액은 0 이상이어야 합니다: " + balance);
        }
        this.memberId = memberId;
        this.balance = balance;
    }

    public void increase(long amount) {
        requirePositive(amount);
        this.balance += amount;
    }

    // 사용 가능 여부는 서비스가 적립 건 합계로 먼저 판단한다. 여기서 모자라면 잔액과 적립 건이 어긋난 것이다
    public void decrease(long amount) {
        requirePositive(amount);
        if (amount > balance) {
            throw new IllegalStateException("포인트 잔액보다 많이 차감할 수 없습니다. memberId=" + memberId
                + ", balance=" + balance + ", amount=" + amount);
        }
        this.balance -= amount;
    }

    private static void requirePositive(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("포인트 변경 금액은 0보다 커야 합니다: " + amount);
        }
    }
}
