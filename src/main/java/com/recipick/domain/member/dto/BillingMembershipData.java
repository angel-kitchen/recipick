package com.recipick.domain.member.dto;

import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record BillingMembershipData(Long membershipId, Long memberId, MembershipType type, MembershipSource source,
        LocalDateTime startAt, LocalDateTime endAt, LocalDateTime activatedAt, LocalDateTime graceEndAt,
        boolean autoRenew, LocalDateTime cancelReservedAt, LocalDateTime terminatedAt,
        LocalDate billingAnchorDate, MembershipConsent consent) {
}
