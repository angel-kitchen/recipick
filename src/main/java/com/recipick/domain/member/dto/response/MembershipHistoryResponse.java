package com.recipick.domain.member.dto.response;

import com.recipick.domain.member.entity.Membership;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record MembershipHistoryResponse(
        Long membershipId,
        String type,
        String source,
        String status,
        LocalDateTime startAt,
        LocalDateTime endAt,
        boolean autoRenew,
        LocalDate billingAnchorDate,
        LocalDateTime cancelReservedAt,
        LocalDateTime terminatedAt,
        Long grantedByAdminId,
        LocalDateTime createdAt
) {
    public static MembershipHistoryResponse from(Membership membership, LocalDateTime now) {
        return new MembershipHistoryResponse(membership.getId(), membership.getType().name(),
                membership.getSource().name(), membership.getStatus(now).map(Enum::name).orElse(null),
                membership.getStartAt(), membership.getEndAt(), membership.isAutoRenew(),
                membership.getBillingAnchorDate(), membership.getCancelReservedAt(), membership.getTerminatedAt(),
                membership.getGrantedByAdmin() == null ? null : membership.getGrantedByAdmin().getId(),
                membership.getCreatedAt());
    }
}
