package com.recipick.domain.member.dto.response;

import com.recipick.domain.member.entity.Membership;
import java.time.LocalDateTime;

public record MembershipCancelReservationResponse(
        Long membershipId,
        String status,
        boolean autoRenew,
        LocalDateTime endAt,
        LocalDateTime cancelReservedAt
) {
    public static MembershipCancelReservationResponse from(Membership membership, LocalDateTime now) {
        return new MembershipCancelReservationResponse(membership.getId(),
                membership.getStatus(now).orElseThrow().name(), membership.isAutoRenew(),
                membership.getEndAt(), membership.getCancelReservedAt());
    }
}
