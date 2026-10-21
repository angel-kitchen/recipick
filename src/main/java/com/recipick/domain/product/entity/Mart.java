package com.recipick.domain.product.entity;



import java.math.BigDecimal;


import com.recipick.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;



@Getter
@Entity
@Table(name = "mart")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Mart extends BaseTimeEntity {

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

    @Column(name = "service_radius_m")
    private Integer serviceRadiusM;


    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private MartStatus status;


    @Builder
    private Mart(
            Long regionId,
            String name,
            BigDecimal latitude,
            BigDecimal longitude,
            Integer serviceRadiusM
    ) {
        this.regionId = regionId;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.serviceRadiusM = serviceRadiusM;
        this.status = MartStatus.OPEN;
    }
}
