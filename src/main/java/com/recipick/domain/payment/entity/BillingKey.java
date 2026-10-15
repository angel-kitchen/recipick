package com.recipick.domain.payment.entity;

import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "billing_key")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingKey extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long memberId;

    @Column(unique = true)
    private String portoneBillingKey;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private BillingKeyStatus status;

    private LocalDateTime issuedAt;

    @Builder
    private BillingKey(Long memberId, String portoneBillingKey, BillingKeyStatus status, LocalDateTime issuedAt) {
        this.memberId = memberId;
        this.portoneBillingKey = portoneBillingKey;
        this.status = status;
        this.issuedAt = issuedAt;
    }
}
