package com.recipick.domain.payment.entity;

import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
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
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 회차 하나에 대한 PortOne 결제 시도. 시도마다 결제 ID("mpay_" + UUID)가 하나라 PortOne 쪽 멱등 단위가 된다.
// 빌링키는 접수 때 고정되고 바꾸는 메서드가 없다(I-17) — 시도 중에 카드가 교체돼도 이 시도는 원래 카드로 끝난다.
// 상태 전이는 첫 갱신·갱신에서 쓴다. PAID·FAILED·CANCELED는 확정이라 바뀌지 않는다.
// 허용되지 않는 전이는 IllegalStateException — 호출자가 잠금 아래에서 상태를 먼저 보므로 예외는 규칙 위반을 드러내는 방어다.
// failureReason·detail에는 고정 코드만 넣는다(대문자·숫자·_·- 64자 이하). PG 원문 문장은 넣지 않는다
@Getter
@Entity
@Table(
    name = "membership_payment_attempt",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_membership_payment_attempt_portone_payment_id",
            columnNames = "portone_payment_id"
        ),
        @UniqueConstraint(
            name = "uk_membership_payment_attempt_payment_id_attempt_no",
            columnNames = {"membership_payment_id", "attempt_no"}
        )
    },
    indexes = {
        @Index(name = "idx_membership_payment_attempt_payment_id_status", columnList = "membership_payment_id, status"),
        @Index(name = "idx_membership_payment_attempt_billing_key_id_status", columnList = "billing_key_id, status")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MembershipPaymentAttempt extends BaseTimeEntity {

    public static final String PAYMENT_ID_PREFIX = "mpay_";
    public static final String REASON_APPLIED_STATE_INCONSISTENT = "APPLIED_STATE_INCONSISTENT";
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9_-]{1,64}$");
    private static final Set<MembershipPaymentAttemptStatus> UNRESOLVED = Set.of(
        MembershipPaymentAttemptStatus.READY,
        MembershipPaymentAttemptStatus.DISPATCHING,
        MembershipPaymentAttemptStatus.UNKNOWN,
        MembershipPaymentAttemptStatus.MANUAL_REVIEW);
    // 결과를 적을 수 있는 상태: PG에 갔을 수 있는 시도만. READY(미발송)·확정은 안 된다
    private static final Set<MembershipPaymentAttemptStatus> RESOLVABLE = Set.of(
        MembershipPaymentAttemptStatus.DISPATCHING,
        MembershipPaymentAttemptStatus.UNKNOWN,
        MembershipPaymentAttemptStatus.MANUAL_REVIEW);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_payment_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_membership_payment_attempt_payment"))
    private MembershipPayment membershipPayment;

    @Column(nullable = false)
    private int attemptNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_key_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_membership_payment_attempt_billing_key"))
    private BillingKey billingKey;

    @Column(nullable = false)
    private String portonePaymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private MembershipPaymentAttemptStatus status;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    // 선점(DISPATCHING) 임대 만료. 지나면 복구 Sweep이 다시 본다
    @Column(nullable = false)
    private LocalDateTime leaseExpiresAt;

    private LocalDateTime dispatchedAt;

    private LocalDateTime completedAt;

    // PortOne이 확인한 결제 완료 시각(KST)
    private LocalDateTime pgPaidAt;

    @Column(length = 500)
    private String failureReason;

    private LocalDateTime opsReviewRequiredAt;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(40)")
    private MembershipOpsReviewReason opsReviewReason;

    // 결제는 됐지만 이용권에 반영하지 않아 돌려줘야 하는 시도(I-19). SYSTEM 환불 COMPLETED 전까지 C-17이 미정산으로 본다
    private LocalDateTime refundRequiredAt;

    // 테스트 픽스처용 생성자. 운영 코드는 ready()로만 새 행을 만든다
    @Builder
    private MembershipPaymentAttempt(
        MembershipPayment membershipPayment,
        int attemptNo,
        BillingKey billingKey,
        String portonePaymentId,
        MembershipPaymentAttemptStatus status,
        LocalDateTime requestedAt,
        LocalDateTime leaseExpiresAt,
        LocalDateTime dispatchedAt,
        LocalDateTime completedAt,
        LocalDateTime pgPaidAt,
        String failureReason,
        LocalDateTime opsReviewRequiredAt,
        MembershipOpsReviewReason opsReviewReason,
        LocalDateTime refundRequiredAt
    ) {
        this.membershipPayment = membershipPayment;
        this.attemptNo = attemptNo;
        this.billingKey = billingKey;
        this.portonePaymentId = portonePaymentId;
        this.status = status;
        this.requestedAt = requestedAt;
        this.leaseExpiresAt = leaseExpiresAt;
        this.dispatchedAt = dispatchedAt;
        this.completedAt = completedAt;
        this.pgPaidAt = pgPaidAt;
        this.failureReason = failureReason;
        this.opsReviewRequiredAt = opsReviewRequiredAt;
        this.opsReviewReason = opsReviewReason;
        this.refundRequiredAt = refundRequiredAt;
    }

    // 접수(TX1). READY = PortOne에 보낸 적 없음이 보장된다. 결제 ID는 새로 만들고 빌링키는 여기서 고정된다(I-17).
    // attemptNo는 호출자가 회차의 기존 최대 + 1로 정한다(UNIQUE가 최종 방어)
    public static MembershipPaymentAttempt ready(MembershipPayment payment, int attemptNo, BillingKey billingKey,
                                                 LocalDateTime now, Duration lease) {
        if (lease == null || lease.isNegative() || lease.isZero() || now == null) {
            throw new IllegalArgumentException("시도 접수 값이 올바르지 않습니다");
        }
        return ready(payment, attemptNo, billingKey, now, now.plus(lease));
    }

    // 임대 만료 시각을 호출자가 정하는 형태(접수 유효 창의 끝을 넘지 않게 — MembershipBillingPeriods.firstChargeLeaseUntil). now 이후여야 한다
    public static MembershipPaymentAttempt ready(MembershipPayment payment, int attemptNo, BillingKey billingKey,
                                                 LocalDateTime now, LocalDateTime leaseExpiresAt) {
        if (payment == null || billingKey == null || now == null || leaseExpiresAt == null
            || !leaseExpiresAt.isAfter(now) || attemptNo < 1) {
            throw new IllegalArgumentException("시도 접수 값이 올바르지 않습니다");
        }
        return new MembershipPaymentAttempt(payment, attemptNo, billingKey, newPaymentId(),
            MembershipPaymentAttemptStatus.READY, now, leaseExpiresAt, null, null, null, null, null, null, null);
    }

    // "mpay_" + 하이픈 없는 UUID(32자) = 37자, [a-z0-9_]. 상품 결제 "pay_"와 접두어로 구분된다(4단계 웹훅 라우팅).
    // PortOne 결제 ID 허용 규칙은 E-3으로 확인한다
    public static String newPaymentId() {
        return PAYMENT_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    // 선점(선점 TX, 시도 PK만 잠금). READY이고 임대 안이면 DISPATCHING. 그 외는 false(예외 아님 — 다른 Worker가 먼저 집었거나 만료).
    // dispatchedAt은 DB(DATETIME(6))에 저장되는 정밀도로 맞춰 둔다 — releaseUnsent가 재조회한 값과 같은지 비교하기 때문
    public boolean claim(LocalDateTime now) {
        if (status != MembershipPaymentAttemptStatus.READY || now == null || !now.isBefore(leaseExpiresAt)) {
            return false;
        }
        this.status = MembershipPaymentAttemptStatus.DISPATCHING;
        this.dispatchedAt = now.truncatedTo(ChronoUnit.MICROS);
        return true;
    }

    // 선점은 했지만 PG POST를 보내지 않은 선점자(플래그가 POST 직전에 꺼짐)만 부른다. 같은 선점인지 dispatchedAt으로 확인하고
    // 맞을 때만 READY로 되돌린다 — READY = 미발송 보장이 유지된다(I-12). 다르면 false(다른 선점·이미 처리됨): 강제로 되돌리지 않는다
    public boolean releaseUnsent(LocalDateTime claimedAt) {
        if (status != MembershipPaymentAttemptStatus.DISPATCHING || claimedAt == null || dispatchedAt == null
            || !dispatchedAt.truncatedTo(ChronoUnit.MICROS).equals(claimedAt.truncatedTo(ChronoUnit.MICROS))) {
            return false;
        }
        this.status = MembershipPaymentAttemptStatus.READY;
        this.dispatchedAt = null;
        return true;
    }

    // 선점이 오래됐는지(복구 대상). DISPATCHING이고 dispatchedAt + 기준 시간이 지났을 때만
    public boolean isDispatchedBefore(LocalDateTime threshold) {
        return status == MembershipPaymentAttemptStatus.DISPATCHING && dispatchedAt != null
            && dispatchedAt.isBefore(threshold);
    }

    public boolean isUnresolved() {
        return UNRESOLVED.contains(status);
    }

    public boolean isFinal() {
        return !isUnresolved();
    }

    // 재조회로 확정된 결제 성공. PG에 갔을 수 있는 상태(DISPATCHING·UNKNOWN·MANUAL_REVIEW)에서만
    public void markPaid(LocalDateTime pgPaidAt, LocalDateTime now) {
        requireResolvable("PAID 확정");
        if (pgPaidAt == null || now == null) {
            throw new IllegalArgumentException("결제 시각과 확정 시각이 필요합니다");
        }
        this.status = MembershipPaymentAttemptStatus.PAID;
        this.pgPaidAt = pgPaidAt;
        this.completedAt = now;
    }

    // 재조회로 확정된 실패(2단계 4-3: 재조회 FAILED로만). reason은 고정 코드
    // 검증(상태·코드·시각)을 모두 끝낸 뒤에만 필드를 바꾼다 — 잘못된 호출을 거절할 때 객체가 반쯤 바뀐 채 남지 않게(김선생 코드 검수 ①)
    public void markFailed(String reason, LocalDateTime now) {
        requireResolvable("FAILED 확정");
        String validatedReason = requireCode(reason);
        if (now == null) {
            throw new IllegalArgumentException("확정 시각이 필요합니다");
        }
        this.status = MembershipPaymentAttemptStatus.FAILED;
        this.failureReason = validatedReason;
        this.completedAt = now;
    }

    // 결과 불명. DISPATCHING → UNKNOWN, 이미 UNKNOWN이면 코드만 갱신. 같은 결제 ID로 재조회해야 하므로 새 ID는 만들지 않는다
    public void markUnknown(String code) {
        if (status != MembershipPaymentAttemptStatus.DISPATCHING && status != MembershipPaymentAttemptStatus.UNKNOWN) {
            throw new IllegalStateException("UNKNOWN으로 바꿀 수 없는 시도 상태입니다: " + this);
        }
        String validatedCode = requireCode(code);
        this.status = MembershipPaymentAttemptStatus.UNKNOWN;
        this.failureReason = validatedCode;
    }

    // 신뢰할 수 없는 PG 결과(ID 불일치·필수값 누락·예상 밖 취소 상태). 자동으로 확정하지 않고 운영 확인으로 넘긴다.
    // 미해결 상태라 C-17이 계속 막는다. DISPATCHING·UNKNOWN에서만
    public void markManualReview(String code, LocalDateTime now) {
        if (status != MembershipPaymentAttemptStatus.DISPATCHING && status != MembershipPaymentAttemptStatus.UNKNOWN) {
            throw new IllegalStateException("MANUAL_REVIEW로 바꿀 수 없는 시도 상태입니다: " + this);
        }
        String validatedCode = requireCode(code);
        if (now == null) {
            throw new IllegalArgumentException("확정 시각이 필요합니다");
        }
        this.status = MembershipPaymentAttemptStatus.MANUAL_REVIEW;
        this.failureReason = validatedCode;
    }

    // 정합성 예외(TX2 0b·사유 없는 C-3 충돌) 뒤 운영 표시만(v4 최종 ①). 상태·pgPaidAt·refundRequiredAt은 바꾸지 않는다 — 미해결
    // 그대로 C-17이 막는다. 이 코드 + 운영 표시가 함께 있을 때만 자동 정합화가 멈춘다(isAutoReconcileHalted). 표시를 지우는 것만으로
    // 해결되지 않는다 — 원인 확인·데이터 정합성 확인·조치 기록 뒤 재처리하고, 표시 해제가 C-17 정산 완료를 뜻하지도 않는다
    public void markAppliedStateInconsistent(LocalDateTime now) {
        if (status != MembershipPaymentAttemptStatus.DISPATCHING && status != MembershipPaymentAttemptStatus.UNKNOWN) {
            throw new IllegalStateException("정합성 표시를 남길 수 없는 시도 상태입니다: " + this);
        }
        if (now == null) {
            throw new IllegalArgumentException("운영 확인 시각과 사유가 필요합니다");
        }
        this.failureReason = REASON_APPLIED_STATE_INCONSISTENT;
        flagOpsReview(now, MembershipOpsReviewReason.PAID_CONFLICT);
    }

    // 자동 정합화 중단 판정(김선생 코드 검수 ④). 정합성 오류 코드와 운영 표시가 함께 있어야 한다 — 다른 사유의 운영 표시(늦은 확인·
    // 환불 실패 등)만으로는 자동 처리를 멈추지 않는다. 복구 조회·reconcile·TX2 진입이 모두 이 판정을 쓴다
    public boolean isAutoReconcileHalted() {
        return isAutoReconcileHalted(failureReason, opsReviewRequiredAt);
    }

    public static boolean isAutoReconcileHalted(String failureReason, LocalDateTime opsReviewRequiredAt) {
        return REASON_APPLIED_STATE_INCONSISTENT.equals(failureReason) && opsReviewRequiredAt != null;
    }

    // READY인 채 임대가 끝난 시도를 접는다(PG 미발송 보장). 그 외는 false — 이미 선점됐거나 처리된 시도는 건드리지 않는다
    public boolean cancelNotDispatched(LocalDateTime now) {
        if (status != MembershipPaymentAttemptStatus.READY || now == null || now.isBefore(leaseExpiresAt)) {
            return false;
        }
        this.status = MembershipPaymentAttemptStatus.CANCELED;
        this.failureReason = "NOT_DISPATCHED";
        this.completedAt = now;
        return true;
    }

    // 결제는 됐는데 이용권에 반영하지 않기로 한 시도(LC 4-6·4-9). PAID에서만. 이미 표시돼 있으면 그대로(멱등)
    public void requireRefund(LocalDateTime now, MembershipOpsReviewReason reason) {
        if (status != MembershipPaymentAttemptStatus.PAID) {
            throw new IllegalStateException("환불 필요 표시는 PAID 시도에서만 가능합니다: " + this);
        }
        if (now == null || reason == null) {
            throw new IllegalArgumentException("환불 필요 시각과 사유가 필요합니다");
        }
        if (refundRequiredAt == null) {
            this.refundRequiredAt = now;
        }
        flagOpsReview(now, reason);
    }

    // 상태는 두고 운영 확인 표시만(I-16: 확정 상태는 바꾸지 않고 표시로 찾는다). 이미 표시돼 있으면 그대로
    public void flagOpsReview(LocalDateTime now, MembershipOpsReviewReason reason) {
        if (now == null || reason == null) {
            throw new IllegalArgumentException("운영 확인 시각과 사유가 필요합니다");
        }
        if (opsReviewRequiredAt == null) {
            this.opsReviewRequiredAt = now;
            this.opsReviewReason = reason;
        }
    }

    private void requireResolvable(String action) {
        if (!RESOLVABLE.contains(status)) {
            throw new IllegalStateException(action + "은(는) PG에 갔을 수 있는 시도에서만 가능합니다: " + this);
        }
    }

    private static String requireCode(String code) {
        if (code == null || !CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("사유 코드 형식이 아닙니다(고정 코드만 허용)");
        }
        return code;
    }

    // 빌링키 원문은 연관 엔티티에 있어 여기 나오지 않지만, 결제 ID·상태만 남겨 로그가 짧고 안전하다
    @Override
    public String toString() {
        return "MembershipPaymentAttempt[id=" + id + ", attemptNo=" + attemptNo + ", status=" + status
            + ", portonePaymentId=" + portonePaymentId + "]";
    }
}
