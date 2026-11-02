package com.recipick.domain.payment.entity;

import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import com.recipick.domain.payment.entity.enums.MembershipRefundOpsReason;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.service.MembershipRefundCalculator.Calculation;
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
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 멤버십 이용료 환불. 환불은 항상 어느 시도(PortOne 결제 ID)를 취소하는지 안다 — 회차가 아니라 시도에 NOT NULL로 묶는다.
// requestedBy는 MEMBER(즉시 해지)·SYSTEM(반영하지 않은 결제의 반환)만 쓴다. SYSTEM 환불 요청 행은 3단계 TX2가 만들고(systemRefundRequested)
// MEMBER 환불 요청 행은 즉시 해지 접수가 만든다(requestedByMember). 환불액·이용일수·수수료·기간은 접수 때 저장하고 실행·재시도 때 다시 계산하지 않는다.
// PG 취소 요청의 멱등 키(pgIdempotencyKey)는 행을 만들 때 정하고, 상점·요청 사유는 최초 실행권을 잡을 때 한 번 고정해 설정이 바뀌어도 덮어쓰지 않는다.
// 상태는 REQUESTED → COMPLETED | FAILED만 전이한다. 결과 불명은 REQUESTED로 남는다 — FAILED는 식별·금액이 맞는 FAILED 취소 내역에서만 쓴다
@Getter
@Entity
@Table(
    name = "membership_refund",
    indexes = {
        @Index(name = "idx_membership_refund_payment_id_status", columnList = "membership_payment_id, status"),
        @Index(
            name = "idx_membership_refund_attempt_requester_status",
            columnList = "membership_payment_attempt_id, requested_by, status"
        ),
        @Index(name = "idx_membership_refund_status_next_check_id", columnList = "status, next_check_at, id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_membership_refund_pg_idempotency_key", columnNames = "pg_idempotency_key"),
        @UniqueConstraint(name = "uk_membership_refund_retry_of", columnNames = "retry_of_refund_id")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MembershipRefund extends BaseTimeEntity {

    public static final String SYSTEM_KEY_PREFIX = "mrf_sys_";
    public static final String MEMBER_KEY_PREFIX = "mrf_member:";
    public static final String PG_KEY_PREFIX = "mrf_v1_";
    public static final String PG_REASON_PREFIX = "RECIPICK_MEMBERSHIP_REFUND_V1_";
    public static final String REASON_MEMBER_TERMINATION = "MEMBER_TERMINATION";
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

    // 원래 수납액(PG 식별 검증의 비교 기준. 환불액과 비교하지 않는다)
    private Long paidAmountSnapshot;

    // MEMBER 환불의 결제기간 [periodStartAt, periodEndAt). SYSTEM 행은 비어 있다
    private LocalDateTime periodStartAt;

    private LocalDateTime periodEndAt;

    private Integer periodDays;

    @Column(length = 64)
    private String requestHash;

    @Column(length = 80)
    private String pgIdempotencyKey;

    @Column(length = 100)
    private String pgStoreId;

    @Column(length = 160)
    private String pgRequestReason;

    private LocalDateTime firstDispatchedAt;

    @Column(nullable = false)
    private long claimToken;

    private LocalDateTime leaseExpiresAt;

    private LocalDateTime nextCheckAt;

    private LocalDateTime opsReviewRequiredAt;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(64)")
    private MembershipRefundOpsReason opsReviewReason;

    private LocalDateTime pgCancelledAt;

    private Long retryOfRefundId;

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
    // 같은 시도의 SYSTEM 환불 행(상태 무관)이 이미 있는지는 호출자가 시도 PK 잠금 아래에서 확인한다
    // (findByMembershipPaymentAttemptIdAndRequestedByOrderByIdAsc).
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
        MembershipRefund refund = new MembershipRefund(attempt.getMembershipPayment(), attempt, actualAmount, 0, 0L, 0L,
            MembershipRefundStatus.REQUESTED, reason, RefundRequester.SYSTEM,
            SYSTEM_KEY_PREFIX + attempt.getId() + "_" + sequence, now, null);
        refund.paidAmountSnapshot = actualAmount;
        refund.pgIdempotencyKey = newPgIdempotencyKey();
        return refund;
    }

    // 즉시 해지의 MEMBER 환불 요청. 계산 결과·기간·원래 수납액(회차 금액)·요청 해시를 접수 시각 그대로 저장한다.
    // 대상 시도는 정상 반영된 PAID(환불 필요 표시 없음) 하나이고, 선택·검증은 호출자가 잠금 아래에서 끝낸다
    public static MembershipRefund requestedByMember(MembershipPayment payment, MembershipPaymentAttempt attempt,
                                                     Calculation calculation, String storedKey, String requestHash,
                                                     LocalDateTime periodStartAt, LocalDateTime periodEndAt,
                                                     LocalDateTime now) {
        if (payment == null || payment.getId() == null || attempt == null || attempt.getId() == null) {
            throw new IllegalArgumentException("저장된 회차와 시도가 필요합니다");
        }
        if (attempt.getStatus() != MembershipPaymentAttemptStatus.PAID || attempt.getRefundRequiredAt() != null) {
            throw new IllegalArgumentException("MEMBER 환불은 정상 반영된 PAID 시도에서만 요청합니다: " + attempt);
        }
        if (calculation == null || payment.getAmount() == null || payment.getAmount() <= 0) {
            throw new IllegalArgumentException("환불 계산 결과와 회차 금액이 필요합니다");
        }
        if (storedKey == null || storedKey.isBlank() || requestHash == null || requestHash.isBlank()) {
            throw new IllegalArgumentException("요청 키와 요청 해시가 필요합니다");
        }
        if (periodStartAt == null || periodEndAt == null || !periodStartAt.isBefore(periodEndAt) || now == null) {
            throw new IllegalArgumentException("결제기간과 접수 시각이 필요합니다");
        }
        MembershipRefund refund = new MembershipRefund(payment, attempt, calculation.refundAmount(),
            calculation.usedDays(), calculation.usedDayFee(), calculation.penaltyAmount(),
            MembershipRefundStatus.REQUESTED, REASON_MEMBER_TERMINATION, RefundRequester.MEMBER, storedKey, now, null);
        refund.paidAmountSnapshot = payment.getAmount();
        refund.periodStartAt = periodStartAt;
        refund.periodEndAt = periodEndAt;
        refund.periodDays = calculation.periodDays();
        refund.requestHash = requestHash;
        refund.pgIdempotencyKey = newPgIdempotencyKey();
        return refund;
    }

    // SYSTEM FAILED 환불의 후속 재실행 행. 금액·사유·시도는 원래 행을 그대로 쓰고 PG 키는 새로 받는다 — 원래 행은 바꾸지 않는다.
    // 같은 실패 행의 후속은 하나뿐이다(retry_of_refund_id UNIQUE가 최종 방어, 호출자가 잠금 아래에서 먼저 확인한다)
    public static MembershipRefund systemRetryOf(MembershipRefund failed, int sequence, LocalDateTime now) {
        if (failed == null || failed.getId() == null) {
            throw new IllegalArgumentException("저장된 환불 행이 필요합니다");
        }
        if (failed.requestedBy != RefundRequester.SYSTEM || failed.status != MembershipRefundStatus.FAILED) {
            throw new IllegalArgumentException("후속 재실행은 FAILED SYSTEM 환불에서만 만듭니다: refundId=" + failed.getId());
        }
        if (sequence < 1 || now == null) {
            throw new IllegalArgumentException("환불 요청 순번과 시각이 필요합니다");
        }
        MembershipPaymentAttempt attempt = failed.membershipPaymentAttempt;
        MembershipRefund refund = new MembershipRefund(failed.membershipPayment, attempt, failed.amount, 0, 0L, 0L,
            MembershipRefundStatus.REQUESTED, failed.reason, RefundRequester.SYSTEM,
            SYSTEM_KEY_PREFIX + attempt.getId() + "_" + sequence, now, null);
        refund.paidAmountSnapshot = failed.paidAmountSnapshot == null ? failed.amount : failed.paidAmountSnapshot;
        refund.retryOfRefundId = failed.getId();
        refund.pgIdempotencyKey = newPgIdempotencyKey();
        return refund;
    }

    private static String newPgIdempotencyKey() {
        return PG_KEY_PREFIX + UUID.randomUUID();
    }

    // ---------- 실행권 ----------

    // 실행권 확보. REQUESTED이고 운영 확인 표시가 없고 임대가 없거나 끝났을 때만 토큰을 올리고 임대를 건다. 상태는 바꾸지 않는다.
    // 토큰은 실행권 갱신·해제, POST 준비, 응답 ID 기록, 다음 확인 예약, 운영 예약 변경을 지킨다 — 확정 결과 반영(TX2)은 토큰이 아니라
    // 잠금 순서·현재 상태·증거 대조로 지킨다
    public boolean claim(LocalDateTime now, Duration lease) {
        if (now == null || lease == null || lease.isNegative() || lease.isZero()) {
            throw new IllegalArgumentException("실행권에는 시각과 임대 기간이 필요합니다");
        }
        if (status != MembershipRefundStatus.REQUESTED || opsReviewRequiredAt != null) {
            return false;
        }
        if (leaseExpiresAt != null && now.isBefore(leaseExpiresAt)) {
            return false;
        }
        this.claimToken = Math.incrementExact(this.claimToken);
        this.leaseExpiresAt = now.plus(lease);
        return true;
    }

    // 현재 실행권 보유자인지(토큰 일치 + 임대 안)
    public boolean holdsClaim(long token, LocalDateTime now) {
        return this.claimToken == token && leaseExpiresAt != null && now.isBefore(leaseExpiresAt);
    }

    // PG 요청에 쓰는 고정값. 비어 있는 값만 채운다 — 설정이 바뀌어도 기존 환불의 값을 새 설정으로 덮어쓰지 않는다.
    // 상점 ID는 서버 설정이고 PG 응답의 상점을 자기 기대값으로 저장하지 않는다
    public void fixDispatchRequest(String storeId) {
        if (this.id == null || storeId == null || storeId.isBlank() || pgIdempotencyKey == null) {
            throw new IllegalArgumentException("고정 요청값에는 저장된 행·상점 ID·PG 키가 필요합니다");
        }
        if (this.pgStoreId == null) {
            this.pgStoreId = storeId;
        }
        if (this.pgRequestReason == null) {
            this.pgRequestReason = PG_REASON_PREFIX + id + "_" + pgIdempotencyKey;
        }
    }

    public boolean hasFixedDispatchRequest() {
        return pgIdempotencyKey != null && pgStoreId != null && pgRequestReason != null;
    }

    // 최초 POST 직전에 한 번만 기록한다. 재시도마다 갱신하지 않는다 — 같은 키 재POST 창과 결과 확인 창의 기준이다
    public void markFirstDispatched(LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("발송 시각이 필요합니다");
        }
        if (firstDispatchedAt == null) {
            firstDispatchedAt = now;
        }
    }

    // 취소 내역 ID 기록. 이미 있으면 같은 ID여야 한다(다르면 증거 충돌 — 호출자가 차단한다)
    public boolean recordCancellationId(String cancellationId) {
        if (cancellationId == null || cancellationId.isBlank()) {
            throw new IllegalArgumentException("취소 내역 ID가 필요합니다");
        }
        if (portoneCancellationId == null) {
            portoneCancellationId = cancellationId;
            return true;
        }
        return portoneCancellationId.equals(cancellationId);
    }

    public void scheduleNextCheck(LocalDateTime at) {
        this.nextCheckAt = at;
    }

    // 결과 확인 창·재조회 간격의 기준 시각. 최초 발송이 있으면 그 시각, 없으면 접수 시각
    public LocalDateTime checkBaseAt() {
        return firstDispatchedAt != null ? firstDispatchedAt : requestedAt;
    }

    // ---------- 확정 ----------

    // 취소 완료. 취소 ID가 없어도 되는 경우는 MEMBER 0원 환불(PG 호출 없음)뿐이다. 임대·예약은 끝낸다
    public void markCompleted(LocalDateTime confirmedAt, LocalDateTime pgCancelledAt, String cancellationId) {
        if (status != MembershipRefundStatus.REQUESTED) {
            throw new IllegalStateException("REQUESTED 환불만 완료할 수 있음: refundId=" + id + ", status=" + status);
        }
        if (confirmedAt == null) {
            throw new IllegalArgumentException("확정 시각이 필요합니다");
        }
        boolean zeroMember = requestedBy == RefundRequester.MEMBER && amount != null && amount == 0L;
        if (cancellationId == null || cancellationId.isBlank()) {
            if (!zeroMember) {
                throw new IllegalArgumentException("취소 내역 ID 없이 완료할 수 있는 환불은 MEMBER 0원뿐입니다: refundId=" + id);
            }
        } else if (!recordCancellationId(cancellationId)) {
            throw new IllegalStateException("저장된 취소 ID와 다른 ID로 완료할 수 없음: refundId=" + id);
        }
        this.status = MembershipRefundStatus.COMPLETED;
        this.completedAt = confirmedAt;
        this.pgCancelledAt = pgCancelledAt;
        this.failureReason = null;
        this.leaseExpiresAt = null;
        this.nextCheckAt = null;
    }

    // 식별·금액이 맞는 FAILED 취소 내역으로만 확정한다. 결과 불명은 REQUESTED로 남긴다
    public void markFailed(String failureCode, String cancellationId) {
        if (status != MembershipRefundStatus.REQUESTED) {
            throw new IllegalStateException("REQUESTED 환불만 실패로 확정할 수 있음: refundId=" + id + ", status=" + status);
        }
        if (failureCode == null || !CODE.matcher(failureCode).matches()) {
            throw new IllegalArgumentException("실패 사유는 고정 코드여야 합니다");
        }
        if (cancellationId == null || cancellationId.isBlank() || !recordCancellationId(cancellationId)) {
            throw new IllegalStateException("취소 내역 ID가 맞는 FAILED 취소에서만 실패로 확정함: refundId=" + id);
        }
        this.status = MembershipRefundStatus.FAILED;
        this.failureReason = failureCode;
        this.leaseExpiresAt = null;
        this.nextCheckAt = null;
    }

    // ---------- 운영 확인 ----------

    // 운영 확인 표시. 강한 차단은 자동 중단 사유를 덮어쓰고(차단이 가려지면 안 된다), 이미 강한 차단이면 첫 차단을 유지한다.
    // 자동 중단 사유끼리는 처음 사유를 유지한다. 상태는 바꾸지 않는다
    public void requireOpsReview(MembershipRefundOpsReason code, LocalDateTime now) {
        if (code == null || now == null) {
            throw new IllegalArgumentException("운영 확인에는 사유와 시각이 필요합니다");
        }
        if (isSettlementBlocked()) {
            return;
        }
        if (opsReviewReason != null && !code.isBlocking()) {
            return;
        }
        this.opsReviewRequiredAt = now;
        this.opsReviewReason = code;
    }

    public boolean isSettlementBlocked() {
        return opsReviewReason != null && opsReviewReason.isBlocking();
    }

    // 자동 실행(복구 조회·재POST)을 멈춰야 하는 상태. REQUESTED이고 운영 확인 표시가 있으면 모두 해당한다
    public boolean isAutoHalted() {
        return status == MembershipRefundStatus.REQUESTED && opsReviewReason != null;
    }

    // 응답의 reviewRequired. 강한 차단이면 true, REQUESTED에 자동 중단 사유가 있으면 true, 정상 확정된 행의 과거 표시는 false
    public boolean isReviewRequired() {
        return isSettlementBlocked() || isAutoHalted();
    }
}
