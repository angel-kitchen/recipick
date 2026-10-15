package com.recipick.domain.order.entity;

import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "orders")
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

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
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

    // TODO: 값 목록 확정 후 enum으로 교체
    @Column(length = 30)
    private String cancelReasonCode;

    @Column(length = 500)
    private String cancelReasonDetail;

    @Builder
    private Order(
            String orderNumber,
            Long memberId,
            Long martId,
            Long pendingMemberId,
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
}
