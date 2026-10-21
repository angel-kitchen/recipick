package com.recipick.domain.order.entity;

import com.recipick.domain.order.entity.enums.CancelReasonCode;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.exception.OrderErrorCode;
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
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "orders",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_orders_member_id_idempotency_key",
                columnNames = {"member_id", "idempotency_key"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 30, unique = true)
    private String orderNumber;

    private Long memberId;

    private Long martId;

    @Column(unique = true)
    private Long pendingMemberId;

    @Column(length = 64)
    private String idempotencyKey;

    @Column(length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private MartStatus martStatus;

    private Long productAmount;

    private Long deliveryFee;

    private Long usedPoint;

    private Long payAmount;

    @Column(precision = 5, scale = 4)
    private BigDecimal pointRate;

    @Column(length = 50)
    private String receiverName;

    @Column(length = 20)
    private String receiverPhone;

    private String address;

    private String detailAddress;

    private Long regionId;

    private LocalDateTime paidAt;

    private LocalDateTime canceledAt;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(30)")
    private CancelReasonCode cancelReasonCode;

    @Column(length = 500)
    private String cancelReasonDetail;

    @Builder
    private Order(
            String orderNumber,
            Long memberId,
            Long martId,
            Long pendingMemberId,
            String idempotencyKey,
            String requestHash,
            OrderStatus status,
            MartStatus martStatus,
            Long productAmount,
            Long deliveryFee,
            Long usedPoint,
            Long payAmount,
            BigDecimal pointRate,
            String receiverName,
            String receiverPhone,
            String address,
            String detailAddress,
            Long regionId
    ) {
        this.orderNumber = orderNumber;
        this.memberId = memberId;
        this.martId = martId;
        this.pendingMemberId = pendingMemberId;
        // 대소문자만 다른 재시도도 같은 키로 판정해야 UNIQUE가 중복 주문을 막는다
        this.idempotencyKey = idempotencyKey == null ? null : idempotencyKey.toLowerCase(Locale.ROOT);
        this.requestHash = requestHash;
        this.status = status;
        this.martStatus = martStatus;
        this.productAmount = productAmount;
        this.deliveryFee = deliveryFee;
        this.usedPoint = usedPoint;
        this.payAmount = payAmount;
        this.pointRate = pointRate;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.address = address;
        this.detailAddress = detailAddress;
        this.regionId = regionId;
    }

    public void markPaid(LocalDateTime paidAt) {
        // null로 PAID가 되면 이후 같은 성공이 와도 멱등 처리로 반환돼 paidAt을 다시 채울 수 없다
        Objects.requireNonNull(paidAt, "paidAt은 필수입니다.");
        if (!status.canTransitionTo(OrderStatus.PAID)) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }
        if (!martStatus.canTransitionTo(MartStatus.RECEIVED)) {
            throw new BusinessException(OrderErrorCode.INVALID_MART_STATUS_TRANSITION);
        }
        this.status = OrderStatus.PAID;
        this.martStatus = MartStatus.RECEIVED;
        this.paidAt = paidAt;
        // 결제 대기를 벗어나야 같은 회원이 새 주문을 만들 수 있다
        this.pendingMemberId = null;
    }

    public void cancel(CancelReasonCode reasonCode, String reasonDetail) {
        if (!status.canTransitionTo(OrderStatus.CANCELED)) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }
        this.status = OrderStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
        this.cancelReasonCode = reasonCode;
        this.cancelReasonDetail = reasonDetail;
        // 결제 대기를 벗어나야 같은 회원이 새 주문을 만들 수 있다
        this.pendingMemberId = null;
    }
}
