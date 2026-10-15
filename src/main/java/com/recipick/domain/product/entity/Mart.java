package com.recipick.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "mart")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Mart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private Long regionId;

    @Column(length = 50)
    private String name;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    private Integer serviceRadiusM;

    // TODO: 값 목록 확정 후 enum으로 교체
    @Column(length = 20)
    private String status;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Mart(
            Long regionId,
            String name,
            BigDecimal latitude,
            BigDecimal longitude,
            Integer serviceRadiusM,
            String status
    ) {
        this.regionId = regionId;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.serviceRadiusM = serviceRadiusM;
        this.status = status;
    }
}
