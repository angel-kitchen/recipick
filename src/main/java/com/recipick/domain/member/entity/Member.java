package com.recipick.domain.member.entity;

import com.recipick.global.entity.BaseTimeEntity;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "member", uniqueConstraints = {
        @UniqueConstraint(name = "uk_member_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_member_phone", columnNames = "phone")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50)
    private String email;

    @Column(length = 50)
    private String name;

    @Column(length = 20)
    private String phone;

    private String passwordHash;

    private Long martId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_address_id")
    private MemberAddress selectedAddress;

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
            LocalDateTime locationAgreedAt,
            LocalDateTime termsAgreedAt,
            LocalDateTime privacyAgreedAt
    ) {
        this.email = email;
        this.name = name;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.martId = martId;
        this.locationAgreedAt = locationAgreedAt;
        this.termsAgreedAt = termsAgreedAt;
        this.privacyAgreedAt = privacyAgreedAt;
    }

    public void selectAddress(MemberAddress address, Long martId) {
        if (address == null || !address.isOwnedBy(this)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        this.selectedAddress = address;
        this.martId = martId;
    }

    public void agreeToLocation(LocalDateTime agreedAt) {
        if (agreedAt == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (locationAgreedAt == null) {
            locationAgreedAt = agreedAt;
        }
    }
}
