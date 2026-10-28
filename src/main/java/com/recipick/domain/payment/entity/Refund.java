package com.recipick.domain.payment.entity;

import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundFailureType;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.global.entity.BaseTimeEntity;
import com.recipick.global.exception.BusinessException;
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
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "refund")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    private Long amount;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private RefundStatus status;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private RefundRequester requestedBy;

    @Column(unique = true)
    private String idempotencyKey;

    @Column(unique = true)
    private String portoneCancellationId;

    // 같은 Idempotency-Key 재요청이 DB만 보고 원래 응답(409 PAYMENT-009 / 500 COMMON-008)을 재현해야 해서 실패 종류를 따로 둔다
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(30)")
    private RefundFailureType failureType;

    @Column(length = 500)
    private String failureReason;

    private LocalDateTime requestedAt;

    private LocalDateTime completedAt;

    @Builder
    private Refund(
        Payment payment,
        Long amount,
        String reason,
        RefundStatus status,
        RefundRequester requestedBy,
        String idempotencyKey,
        LocalDateTime requestedAt
    ) {
        this.payment = payment;
        this.amount = amount;
        this.reason = reason;
        this.status = status;
        this.requestedBy = requestedBy;
        this.idempotencyKey = idempotencyKey;
        this.requestedAt = requestedAt;
    }

    public static Refund requestedBySystem(
        Payment payment,
        Long amount,
        String reason,
        String idempotencyKey,
        LocalDateTime requestedAt
    ) {
        return Refund.builder()
            .payment(payment)
            .amount(amount)
            .reason(reason)
            .status(RefundStatus.REQUESTED)
            .requestedBy(RefundRequester.SYSTEM)
            .idempotencyKey(idempotencyKey)
            .requestedAt(requestedAt)
            .build();
    }

    // 회원 취소는 클라이언트의 Idempotency-Key를 그대로 저장한다. 같은 키의 재요청이 이 행을 다시 찾는다.
    // 전액 취소만 허용하므로 금액은 호출자가 정하지 않고 Payment.amount를 그대로 쓴다
    public static Refund requestedByMember(
        Payment payment,
        String reason,
        String idempotencyKey,
        LocalDateTime requestedAt
    ) {
        return requestedAfterPay(RefundRequester.MEMBER, payment, reason, idempotencyKey, requestedAt);
    }

    // 마트 거절 환불. 관리자 거절 API의 Idempotency-Key를 그대로 저장하고, 전액 취소라 금액은 Payment.amount다.
    // ADMIN은 레시픽 안에서 이 환불이 마트 거절 경로라는 뜻이다. PortOne에 보내는 요청 주체(requester=ADMIN)와는 다른 값이다
    public static Refund requestedByAdmin(
        Payment payment,
        String reason,
        String idempotencyKey,
        LocalDateTime requestedAt
    ) {
        return requestedAfterPay(RefundRequester.ADMIN, payment, reason, idempotencyKey, requestedAt);
    }

    // 결제 후 전액 취소 환불의 공통 생성. Payment.requestCancel() 뒤에만 만든다 —
    // 취소 요청 상태가 아닌 결제에 환불 행이 생기면 PortOne 호출 근거가 없다
    private static Refund requestedAfterPay(
        RefundRequester requestedBy,
        Payment payment,
        String reason,
        String idempotencyKey,
        LocalDateTime requestedAt
    ) {
        if (payment.getStatus() != PaymentStatus.CANCEL_REQUESTED) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }
        return Refund.builder()
            .payment(payment)
            .amount(payment.getAmount())
            .reason(reason)
            .status(RefundStatus.REQUESTED)
            .requestedBy(requestedBy)
            .idempotencyKey(idempotencyKey)
            .requestedAt(requestedAt)
            .build();
    }

    // PortOne 콘솔·관리자 화면에서 이미 끝난 전액 취소를 기록한다. PG를 호출하지 않으므로 멱등키 대신
    // portoneCancellationId UNIQUE가 같은 취소의 중복 기록을 막는다. 결제를 외부 취소로 먼저 닫은 뒤에만 만든다 —
    // 순서가 바뀌면 PAID 결제에 완료 환불이 붙는다. 전액 취소만 반영하므로 금액은 Payment.amount를 그대로 쓴다
    public static Refund completedByExternal(
        Payment payment,
        String portoneCancellationId,
        String reason,
        LocalDateTime requestedAt,
        LocalDateTime completedAt
    ) {
        if (payment.getStatus() != PaymentStatus.CANCELED) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }
        Objects.requireNonNull(requestedAt, "requestedAt은 필수입니다.");
        Refund refund = Refund.builder()
            .payment(payment)
            .amount(payment.getAmount())
            .reason(reason)
            .status(RefundStatus.REQUESTED)
            .requestedBy(RefundRequester.EXTERNAL)
            .requestedAt(requestedAt)
            .build();
        // 완료 전이의 검사(취소 ID·완료 시각 필수)를 그대로 거친다
        refund.complete(portoneCancellationId, completedAt);
        return refund;
    }

    // PortOne이 REQUESTED(비동기 처리 중)로 답했다. 웹훅·재조회가 이 ID로 환불을 찾으므로 상태는 두고 ID만 남긴다
    public void recordRequested(String portoneCancellationId) {
        requireRequested();
        Objects.requireNonNull(portoneCancellationId, "portoneCancellationId는 필수입니다.");
        applyCancellationId(portoneCancellationId);
    }

    // 완료 시각은 PG가 확인한 값이어야 한다. 없으면 완료로 바꾸지 않고 REQUESTED로 둔다(recordRequested)
    public void complete(String portoneCancellationId, LocalDateTime completedAt) {
        requireRequested();
        Objects.requireNonNull(portoneCancellationId, "portoneCancellationId는 필수입니다.");
        Objects.requireNonNull(completedAt, "completedAt은 필수입니다.");
        applyCancellationId(portoneCancellationId);
        this.status = RefundStatus.COMPLETED;
        this.completedAt = completedAt;
    }

    // PG가 처리 전에 거절(400·401·403)한 경우는 cancellation 객체가 없어 portoneCancellationId가 null이다
    public void fail(String portoneCancellationId, RefundFailureType failureType, String failureReason) {
        requireRequested();
        Objects.requireNonNull(failureType, "failureType은 필수입니다.");
        Objects.requireNonNull(failureReason, "failureReason은 필수입니다.");
        // PortOne이 FAILED를 명시한 응답에는 cancellation 객체가 있으므로 ID도 반드시 있다
        if (failureType == RefundFailureType.PG_FAILED) {
            Objects.requireNonNull(portoneCancellationId, "PG_FAILED는 portoneCancellationId가 필수입니다.");
        }
        // 거절 응답에는 cancellation 객체가 없다. ID가 생긴 환불은 PortOne을 다시 부르지 않으므로 거절 응답 자체가 올 수 없다
        if (failureType == RefundFailureType.REQUEST_REJECTED
            && (portoneCancellationId != null || this.portoneCancellationId != null)) {
            throw new IllegalStateException("REQUEST_REJECTED는 PortOne 취소 ID를 가질 수 없습니다.");
        }
        applyCancellationId(portoneCancellationId);
        this.status = RefundStatus.FAILED;
        this.failureType = failureType;
        this.failureReason = failureReason;
    }

    // 이미 끝난 환불을 다시 바꾸면 완료·실패 기록이 덮어써진다
    private void requireRequested() {
        if (this.status != RefundStatus.REQUESTED) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }
    }

    // 환불 행 하나가 PortOne 취소 작업 하나다. 한 번 정해진 ID는 바뀌지 않고, 같은 서버 키의 재호출은 같은 ID를 돌려준다
    private void applyCancellationId(String cancellationId) {
        if (cancellationId == null) {
            return;
        }
        if (this.portoneCancellationId != null && !this.portoneCancellationId.equals(cancellationId)) {
            throw new IllegalStateException("기존 PortOne 취소 ID와 일치하지 않습니다.");
        }
        this.portoneCancellationId = cancellationId;
    }
}
