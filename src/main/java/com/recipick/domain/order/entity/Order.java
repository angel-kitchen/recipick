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
import jakarta.persistence.Index;
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
                name = Order.IDEMPOTENCY_KEY_CONSTRAINT,
                columnNames = {"member_id", "idempotency_key"}
        ),
        // 목록은 id 내림차순으로 읽는다. 보조 인덱스 끝에 PK가 붙어 정렬까지 인덱스 순서로 해결된다
        indexes = {
                @Index(name = "idx_orders_member_id", columnList = "member_id"),
                @Index(name = "idx_orders_mart_id_mart_status", columnList = "mart_id, mart_status")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {

    public static final String IDEMPOTENCY_KEY_CONSTRAINT = "uk_orders_member_id_idempotency_key";

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
        // CANCEL_REQUESTED → PAID는 결제 성공이 아니라 회원 취소 실패 복귀이므로 여기서 받지 않는다
        if (status != OrderStatus.PENDING_PAYMENT) {
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

    // 금액은 재고를 잠근 뒤의 가격으로 계산하는데, 락 순서상 주문을 재고보다 먼저 저장해야 해서 생성 뒤에 채운다
    public void confirmAmounts(long productAmount, long deliveryFee, long payAmount) {
        if (status != OrderStatus.PENDING_PAYMENT || this.payAmount != null) {
            throw new IllegalStateException("결제 대기 중이고 금액이 정해지지 않은 주문만 금액을 확정할 수 있습니다.");
        }
        this.productAmount = productAmount;
        this.deliveryFee = deliveryFee;
        this.payAmount = payAmount;
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

    // 마트가 준비를 시작하면 회원 직접 취소·환불을 받지 않는다
    public boolean isCancelableByMember() {
        return status == OrderStatus.PAID && martStatus == MartStatus.RECEIVED;
    }

    public void requestMemberCancel(String reasonDetail) {
        if (!isCancelableByMember()) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_CANCELABLE);
        }
        this.status = OrderStatus.CANCEL_REQUESTED;
        this.cancelReasonCode = CancelReasonCode.MEMBER_CANCELED;
        this.cancelReasonDetail = reasonDetail;
    }

    // 마트는 접수중인 결제 완료 주문만 수락한다. 취소 진행 중·종료된 주문과 이미 준비를 시작한 주문은 수락 대상이 아니다
    public boolean isAcceptableByMart() {
        return status == OrderStatus.PAID && martStatus.canTransitionTo(MartStatus.PREPARING);
    }

    public void acceptByMart() {
        if (!isAcceptableByMart()) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_ACCEPTABLE);
        }
        this.martStatus = MartStatus.PREPARING;
    }

    public void restorePaidAfterMemberCancelFailure() {
        // 마트 거절은 이미 처리할 수 없다고 판정됐으므로 PG 취소가 실패해도 정상 주문으로 되돌리지 않는다
        if (cancelReasonCode != CancelReasonCode.MEMBER_CANCELED || !status.canTransitionTo(OrderStatus.PAID)) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }
        this.status = OrderStatus.PAID;
        // 다시 취소할 때 새 사유를 받을 수 있어야 하고, 취소 시도는 상태 이력에 남아 있다
        this.cancelReasonCode = null;
        this.cancelReasonDetail = null;
    }

    public void markRefunded() {
        // 환불 완료 이력에 남길 취소 사유가 없으면 어떤 취소로 끝났는지 알 수 없다.
        // PAID → REFUNDED는 외부 취소 전용이라 여기서 받지 않는다
        if (status != OrderStatus.CANCEL_REQUESTED || cancelReasonCode == null) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }
        this.status = OrderStatus.REFUNDED;
        this.canceledAt = LocalDateTime.now();
    }

    // PG가 이미 취소를 끝낸 외부 취소는 기다릴 PG 결과가 없어 CANCEL_REQUESTED를 거치지 않는다.
    // 취소 진행 중인 주문은 그 취소의 완료이므로 markRefunded로 끝낸다
    public void closeByExternalCancel(String reasonDetail) {
        if (status != OrderStatus.PAID) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }
        this.status = OrderStatus.REFUNDED;
        this.canceledAt = LocalDateTime.now();
        this.cancelReasonCode = CancelReasonCode.EXTERNAL_CANCELED;
        this.cancelReasonDetail = reasonDetail;
    }
}
