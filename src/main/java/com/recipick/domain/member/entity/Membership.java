package com.recipick.domain.member.entity;

import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "membership")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Membership extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by_admin_id")
    private Admin grantedByAdmin;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private MembershipType type;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private MembershipSource source;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private boolean autoRenew;

    private LocalDate billingAnchorDate;

    private Long consentedAmount;

    @Column(length = 20)
    private String consentTextVersion;

    private LocalDateTime autopayConsentedAt;

    private LocalDateTime cancelReservedAt;

    private LocalDateTime terminatedAt;

    @Builder
    private Membership(
            Member member,
            Admin grantedByAdmin,
            MembershipType type,
            MembershipSource source,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean autoRenew,
            LocalDate billingAnchorDate,
            Long consentedAmount,
            String consentTextVersion,
            LocalDateTime autopayConsentedAt
    ) {
        this.member = member;
        this.grantedByAdmin = grantedByAdmin;
        this.type = type;
        this.source = source;
        this.startAt = startAt;
        this.endAt = endAt;
        this.autoRenew = autoRenew;
        this.billingAnchorDate = billingAnchorDate;
        this.consentedAmount = consentedAmount;
        this.consentTextVersion = consentTextVersion;
        this.autopayConsentedAt = autopayConsentedAt;
    }
}
