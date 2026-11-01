package com.recipick.domain.payment.entity;

import com.recipick.domain.payment.entity.enums.MembershipPaymentStatus;
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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 멤버십 결제 회차(이용기간 하나의 청구). PortOne 호출 단위는 MembershipPaymentAttempt로 분리됐다 —
// 회차는 "이 기간을 얼마에 청구한다"는 사실과 확정 결과만 갖고, 빌링키·결제 ID·시도 횟수는 시도 행이 갖는다.
// 상태 전이는 3단계(첫 결제)·4단계(갱신)가 쓴다. PAID·FAILED는 확정이라 어떤 메서드도 바꾸지 않는다(I-16) —
// 확정 상태에서 전이 메서드를 부르면 IllegalStateException으로 호출 순서 버그를 드러낸다(호출자는 잠금 아래에서 상태를 먼저 본다).
// 운영 DB의 기존 컬럼(billing_key_id·portone_payment_id·attempt_count·last_attempt_at)은 매핑에서 뺐고 Expand 배포 뒤
// 별도 승인 절차(Contract)로 지운다 — docs/deploy-implementation.md
@Getter
@Entity
@Table(
    name = "membership_payment",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_membership_payment_membership_id_period_start_date",
            columnNames = {"membership_id", "period_start_date"}
        ),
        @UniqueConstraint(name = "uk_membership_payment_idempotency_key", columnNames = "idempotency_key")
    },
    indexes = @Index(name = "idx_membership_payment_membership_id_status", columnList = "membership_id, status")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MembershipPayment extends BaseTimeEntity {

    private static final Set<MembershipPaymentStatus> UNRESOLVED = Set.of(
        MembershipPaymentStatus.PENDING,
        MembershipPaymentStatus.RETRY_WAITING,
        MembershipPaymentStatus.PAYMENT_METHOD_REQUIRED,
        MembershipPaymentStatus.UNKNOWN,
        MembershipPaymentStatus.MANUAL_REVIEW);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 물리 FK 없음(기존과 같음). 이용권은 member 도메인 소유라 ID만 둔다
    private Long membershipId;

    // 바로 구독(회원 요청)만 채운다. "{memberId}:{소문자 키}". 전환·갱신 회차는 null
    @Column(length = 100)
    private String idempotencyKey;

    // 바로 구독 요청 본문의 SHA-256
    @Column(length = 64)
    private String requestHash;

    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(30)")
    private MembershipPaymentStatus status;

    private LocalDate billingDate;

    private LocalDate periodStartDate;

    private LocalDate periodEndDate;

    // 갱신 실패 유예 마감. 생성 때만 정하고(I-14) 첫 결제는 null — 그래서 "null이면 첫 결제 회차"로 종류를 가린다
    private LocalDateTime graceDeadlineAt;

    private LocalDateTime nextRetryAt;

    @Column(length = 500)
    private String failureReason;

    private LocalDateTime paidAt;

    // 멤버십 도메인에 결과를 반영한 시각(TX2)
    private LocalDateTime membershipAppliedAt;

    // 테스트 픽스처용 생성자. 운영 코드는 pendingFirst(3단계)·4단계 갱신 팩토리로만 새 행을 만든다
    @Builder
    private MembershipPayment(
        Long membershipId,
        String idempotencyKey,
        String requestHash,
        Long amount,
        MembershipPaymentStatus status,
        LocalDate billingDate,
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        LocalDateTime graceDeadlineAt,
        LocalDateTime nextRetryAt,
        String failureReason,
        LocalDateTime paidAt,
        LocalDateTime membershipAppliedAt
    ) {
        this.membershipId = membershipId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.amount = amount;
        this.status = status;
        this.billingDate = billingDate;
        this.periodStartDate = periodStartDate;
        this.periodEndDate = periodEndDate;
        this.graceDeadlineAt = graceDeadlineAt;
        this.nextRetryAt = nextRetryAt;
        this.failureReason = failureReason;
        this.paidAt = paidAt;
        this.membershipAppliedAt = membershipAppliedAt;
    }

    // 첫 결제 회차(바로 구독·체험 전환). 기간은 임시(접수 시각 기준)이고 TX2가 PG paidAt 기준으로 덧씌운다(LC 3-4).
    // 첫 결제는 유예 마감이 없다(graceDeadlineAt null). 전환 회차는 멱등 키·해시가 null이다
    public static MembershipPayment pendingFirst(Long membershipId, String idempotencyKey, String requestHash,
                                                 long amount, LocalDate provisionalStart, LocalDate provisionalEnd) {
        if (membershipId == null || amount <= 0 || provisionalStart == null || provisionalEnd == null
            || !provisionalStart.isBefore(provisionalEnd)) {
            throw new IllegalArgumentException("첫 결제 회차의 이용권·금액·임시 기간이 올바르지 않습니다");
        }
        return new MembershipPayment(membershipId, idempotencyKey, requestHash, amount, MembershipPaymentStatus.PENDING,
            provisionalStart, provisionalStart, provisionalEnd, null, null, null, null, null);
    }

    public boolean isUnresolved() {
        return UNRESOLVED.contains(status);
    }

    public boolean isApplied() {
        return membershipAppliedAt != null;
    }

    // 첫 결제 회차인지(LC: 첫 결제만 유예 마감이 없다). 갱신 회차의 늦은 결과는 4단계가 다른 규칙으로 본다
    public boolean isFirstPayment() {
        return graceDeadlineAt == null;
    }

    // PG paidAt 기준 확정 기간으로 덧씌운다. 미해결일 때만 — 확정 회차의 기간은 바꾸지 않는다(I-16)
    public void applyFirstPeriod(LocalDate start, LocalDate end) {
        requireUnresolved("기간 반영");
        if (start == null || end == null || !start.isBefore(end)) {
            throw new IllegalArgumentException("첫 결제 확정 기간이 올바르지 않습니다");
        }
        this.billingDate = start;
        this.periodStartDate = start;
        this.periodEndDate = end;
    }

    // 미해결 → PAID. paidAt은 PG가 확인한 시각, appliedAt은 이용권 반영 시각(같은 TX2)
    public void markPaid(LocalDateTime pgPaidAt, LocalDateTime appliedAt) {
        requireUnresolved("PAID 확정");
        if (pgPaidAt == null || appliedAt == null) {
            throw new IllegalArgumentException("결제 시각과 반영 시각이 필요합니다");
        }
        this.status = MembershipPaymentStatus.PAID;
        this.paidAt = pgPaidAt;
        this.membershipAppliedAt = appliedAt;
        this.failureReason = null;
    }

    // 미해결 → FAILED(조회로 확정된 실패, 또는 PG에 간 적 없이 접은 회차)
    public void markFailed(String reason) {
        requireUnresolved("FAILED 확정");
        this.status = MembershipPaymentStatus.FAILED;
        this.failureReason = reason;
    }

    // 반영하지 않기로 한 PAID의 회차 닫기(LC 4-6·4-9). 시도는 PAID로 남고 SYSTEM 환불 요청이 함께 저장된다
    public void closeUnapplied(String reason) {
        markFailed(reason);
    }

    // PENDING → UNKNOWN. 이미 UNKNOWN이면 그대로. 재조회로만 확정한다
    public void markUnknown() {
        if (status == MembershipPaymentStatus.UNKNOWN) {
            return;
        }
        if (status != MembershipPaymentStatus.PENDING) {
            throw new IllegalStateException("UNKNOWN으로 바꿀 수 없는 회차 상태입니다: " + this);
        }
        this.status = MembershipPaymentStatus.UNKNOWN;
    }

    // PENDING·UNKNOWN → MANUAL_REVIEW. 신뢰할 수 없는 PG 결과(상세 설계 5-1 9번). 미해결 상태라 C-17이 계속 막는다
    public void markManualReview() {
        if (status == MembershipPaymentStatus.MANUAL_REVIEW) {
            return;
        }
        if (status != MembershipPaymentStatus.PENDING && status != MembershipPaymentStatus.UNKNOWN) {
            throw new IllegalStateException("MANUAL_REVIEW로 바꿀 수 없는 회차 상태입니다: " + this);
        }
        this.status = MembershipPaymentStatus.MANUAL_REVIEW;
    }

    private void requireUnresolved(String action) {
        if (!isUnresolved()) {
            throw new IllegalStateException(action + "은(는) 미해결 회차에서만 가능합니다: " + this);
        }
    }

    @Override
    public String toString() {
        return "MembershipPayment[id=" + id + ", membershipId=" + membershipId + ", status=" + status + "]";
    }
}
