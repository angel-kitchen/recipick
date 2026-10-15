package com.recipick.domain.member.entity;

import com.recipick.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Getter
@Entity
@Table(name = "member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50, unique = true)
    private String email;

    @Column(length = 50)
    private String name;

    @Column(length = 20, unique = true)
    private String phone;

    private String passwordHash;

    private Long martId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id")
    private Region region;

    private String deliveryAddress;

    private String deliveryDetailAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_region_id")
    private Region deliveryRegion;

    private LocalDateTime locationAgreedAt;

    private LocalDateTime termsAgreedAt;

    private LocalDateTime privacyAgreedAt;

    @Builder
    private Member(
            String email,
            String name,
            String phone,
            String passwordHash,
            Long martId,
            Region region,
            String deliveryAddress,
            String deliveryDetailAddress,
            Region deliveryRegion,
            LocalDateTime locationAgreedAt,
            LocalDateTime termsAgreedAt,
            LocalDateTime privacyAgreedAt
    ) {
        this.email = email;
        this.name = name;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.martId = martId;
        this.region = region;
        this.deliveryAddress = deliveryAddress;
        this.deliveryDetailAddress = deliveryDetailAddress;
        this.deliveryRegion = deliveryRegion;
        this.locationAgreedAt = locationAgreedAt;
        this.termsAgreedAt = termsAgreedAt;
        this.privacyAgreedAt = privacyAgreedAt;
    }
}
