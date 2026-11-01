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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 멤버십 결제 회차(이용기간 하나의 청구). PortOne 호출 단위는 MembershipPaymentAttempt로 분리됐다 —
// 회차는 "이 기간을 얼마에 청구한다"는 사실과 확정 결과만 갖고, 빌링키·결제 ID·시도 횟수는 시도 행이 갖는다.
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 물리 FK 없음(기존과 같음). 이용권은 member 도메인 소유라 ID만 둔다
    private Long membershipId;

    // 바로 구독(회원 요청)만 채운다. "{memberId}:{소문자 키}". 갱신 회차는 null
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

    // 갱신 실패 유예 마감. 생성 때만 정하고(I-14) 첫 결제는 null
    private LocalDateTime graceDeadlineAt;

    private LocalDateTime nextRetryAt;

    @Column(length = 500)
    private String failureReason;

    private LocalDateTime paidAt;

    // 멤버십 도메인에 결과를 반영한 시각(TX2)
    private LocalDateTime membershipAppliedAt;

    // 2단계는 테스트 픽스처와 4단계 접수가 쓸 생성자만 둔다
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
}
