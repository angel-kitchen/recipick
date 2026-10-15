package com.recipick.domain.payment.entity;

import com.recipick.domain.payment.entity.enums.PointHistoryType;
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
@Table(name = "point_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_earning_id")
    private PointEarning pointEarning;

    private Long memberId;

    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private PointHistoryType type;

    private Long amount;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private PointHistory(PointEarning pointEarning, Long memberId, Long orderId, PointHistoryType type, Long amount) {
        this.pointEarning = pointEarning;
        this.memberId = memberId;
        this.orderId = orderId;
        this.type = type;
        this.amount = amount;
    }
}
