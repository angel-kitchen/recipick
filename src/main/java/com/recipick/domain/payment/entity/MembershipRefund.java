package com.recipick.domain.payment.entity;

import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 멤버십 이용료 환불. 환불은 항상 어느 시도(PortOne 결제 ID)를 취소하는지 안다 — 회차가 아니라 시도에 NOT NULL로 묶는다.
// requestedBy는 MEMBER(즉시 해지)·SYSTEM(반영하지 않은 결제의 반환)만 쓴다. SYSTEM 환불 요청 행은 3단계 TX2가 만들고(systemRefundRequested)
@Getter
@Entity
@Table(
    name = "membership_refund",
    indexes = {
        @Index(name = "idx_membership_refund_payment_id_status", columnList = "membership_payment_id, status"),
        @Index(
            name = "idx_membership_refund_attempt_requester_status",
            columnList = "membership_payment_attempt_id, requested_by, status"
        )
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MembershipRefund extends BaseTimeEntity {

    public static final String SYSTEM_KEY_PREFIX = "mrf_sys_";
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9_-]{1,64}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_payment_id")
    private MembershipPayment membershipPayment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_payment_attempt_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_membership_refund_attempt"))
    private MembershipPaymentAttempt membershipPaymentAttempt;

    private Long amount;

    private int usedDays;

    private Long usedDayFee;

    private Long penaltyAmount;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private MembershipRefundStatus status;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private RefundRequester requestedBy;

    @Column(unique = true)
    private String idempotencyKey;

    @Column(unique = true)
    private String portoneCancellationId;

    @Column(length = 500)
    private String failureReason;

    private LocalDateTime requestedAt;

    private LocalDateTime completedAt;

    @Builder
    private MembershipRefund(
        MembershipPayment membershipPayment,
        MembershipPaymentAttempt membershipPaymentAttempt,
        Long amount,
        int usedDays,
        Long usedDayFee,
        Long penaltyAmount,
        MembershipRefundStatus status,
        String reason,
        RefundRequester requestedBy,
        String idempotencyKey,
        LocalDateTime requestedAt,
        LocalDateTime completedAt
    ) {
        this.membershipPayment = membershipPayment;
        this.membershipPaymentAttempt = membershipPaymentAttempt;
        this.amount = amount;
        this.usedDays = usedDays;
        this.usedDayFee = usedDayFee;
        this.penaltyAmount = penaltyAmount;
        this.status = status;
        this.reason = reason;
        this.requestedBy = requestedBy;
        this.idempotencyKey = idempotencyKey;
        this.requestedAt = requestedAt;
        this.completedAt = completedAt;
    }

    // 반영하지 않은 PAID 시도의 SYSTEM 환불 요청
    // actualAmount = PG가 실제로 수납한 금액(재조회 totalAmount). 회차 금액(3,900)을 기본값으로 쓰지 않는다 — 금액 불일치
    // PAID는 실제 수납액을 돌려줘야 하고, 식별·금액이 불확실한 결과(UNTRUSTED)에서는 호출자가 아예 부르지 않는다.
    // 검증 실패는 IllegalArgumentException → TX2 전체 롤백(시도·회차 종료 기록까지 함께 되돌아간다).
    // 같은 시도에 REQUESTED가 이미 있는지는 호출자가 시도 PK 잠금 아래에서 확인한다(existsByMembershipPaymentAttemptIdAndStatus).
    public static MembershipRefund systemRefundRequested(MembershipPaymentAttempt attempt, long actualAmount,
                                                         String reason, int sequence, LocalDateTime now) {
        if (attempt == null || attempt.getId() == null) {
            throw new IllegalArgumentException("저장된 시도가 필요합니다");
        }
        if (attempt.getStatus() != MembershipPaymentAttemptStatus.PAID || attempt.getRefundRequiredAt() == null) {
            throw new IllegalArgumentException("SYSTEM 환불은 환불 필요 표시가 된 PAID 시도에서만 요청합니다: " + attempt);
        }
        if (actualAmount <= 0) {
            throw new IllegalArgumentException("환불 금액은 실제 수납액이어야 합니다");
        }
        if (reason == null || !CODE.matcher(reason).matches()) {
            throw new IllegalArgumentException("환불 사유는 고정 코드여야 합니다");
        }
        if (sequence < 1 || now == null) {
            throw new IllegalArgumentException("환불 요청 순번과 시각이 필요합니다");
        }
        return new MembershipRefund(attempt.getMembershipPayment(), attempt, actualAmount, 0, 0L, 0L,
            MembershipRefundStatus.REQUESTED, reason, RefundRequester.SYSTEM,
            SYSTEM_KEY_PREFIX + attempt.getId() + "_" + sequence, now, null);
    }
}
