package com.recipick.domain.order.entity;

import com.recipick.domain.order.entity.enums.OrderStatusActor;
import com.recipick.domain.order.entity.enums.OrderStatusType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "order_status_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OrderStatusType statusType;

    // statusType에 따라 OrderStatus 또는 MartStatus 값이 들어가므로 String으로 둔다
    @Column(length = 20)
    private String fromStatus;

    @Column(length = 20)
    private String toStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OrderStatusActor actor;

    private Long changedByAdminId;

    @Column(length = 500)
    private String reason;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private OrderStatusHistory(
            Order order,
            OrderStatusType statusType,
            String fromStatus,
            String toStatus,
            OrderStatusActor actor,
            Long changedByAdminId,
            String reason
    ) {
        this.order = order;
        this.statusType = statusType;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
        this.changedByAdminId = changedByAdminId;
        this.reason = reason;
    }
}
