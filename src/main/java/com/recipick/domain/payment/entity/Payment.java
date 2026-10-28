package com.recipick.domain.payment.entity;

import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.global.entity.BaseTimeEntity;
import com.recipick.global.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "payment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private PaymentMethod paymentMethod;

    @Column(unique = true)
    private String portonePaymentId;

    private String portoneTransactionId;

    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(500)")
    private PaymentFailureReason failureReason;

    private LocalDateTime expiresAt;

    private LocalDateTime paidAt;

    private LocalDateTime canceledAt;

    @Builder
    private Payment(
        Long orderId,
        PaymentMethod paymentMethod,
        String portonePaymentId,
        Long amount,
        PaymentStatus status,
        LocalDateTime expiresAt
    ) {
        this.orderId = orderId;
        this.paymentMethod = paymentMethod;
        this.portonePaymentId = portonePaymentId;
        this.amount = amount;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    public void markPaid(String portoneTransactionId, LocalDateTime paidAt) {
        if (this.status != PaymentStatus.IN_PROGRESS) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }
        this.status = PaymentStatus.PAID;
        this.portoneTransactionId = portoneTransactionId;
        this.paidAt = paidAt;
    }

    public void fail(
        PaymentFailureReason failureReason,
        String portoneTransactionId
    ) {
        if (this.status != PaymentStatus.IN_PROGRESS) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }
        this.status = PaymentStatus.FAILED;
        this.failureReason = failureReason;
        this.portoneTransactionId = portoneTransactionId;
    }

    // PortOne 호출 전에 커밋해 둔다. 호출 중 같은 결제에 다른 취소가 끼어들면 이 상태로 걸러진다(COMMON-006)
    public void requestCancel() {
        if (this.status != PaymentStatus.PAID) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }
        this.status = PaymentStatus.CANCEL_REQUESTED;
    }

    // PortOne이 취소 완료를 확인했을 때만 부른다. 취소 시각은 PG가 준 값이어야 하므로 서버 시각으로 대신 채우지 않는다
    public void markCanceled(LocalDateTime canceledAt) {
        if (this.status != PaymentStatus.CANCEL_REQUESTED) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }
        Objects.requireNonNull(canceledAt, "canceledAt은 필수입니다.");
        this.status = PaymentStatus.CANCELED;
        this.canceledAt = canceledAt;
    }

    // PortOne 콘솔·관리자 화면에서 이미 끝난 전액 취소를 반영한다. 서버가 취소를 요청한 적이 없으므로 CANCEL_REQUESTED를
    // 거치지 않는다 — 거치게 하면 서버가 요청하지 않은 취소 요청 기록이 생긴다. 취소 시각은 PG가 준 값이어야 한다
    public void markExternallyCanceled(LocalDateTime canceledAt) {
        if (this.status != PaymentStatus.PAID) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }
        Objects.requireNonNull(canceledAt, "canceledAt은 필수입니다.");
        this.status = PaymentStatus.CANCELED;
        this.canceledAt = canceledAt;
    }

    // PortOne이 FAILED를 명시했거나 처리 전에 거절(400·401·403)했을 때만 되돌린다. 결과를 모르는 응답에서는 부르지 않는다
    public void restorePaid() {
        if (this.status != PaymentStatus.CANCEL_REQUESTED) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }
        this.status = PaymentStatus.PAID;
    }

}
