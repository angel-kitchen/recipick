package com.recipick.domain.member.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record MembershipMeResponse(boolean active, Current current, boolean trialEligible) {

    public record Current(Long membershipId, String type, String source, LocalDateTime startAt,
            LocalDateTime endAt, boolean autoRenew, LocalDate billingAnchorDate, LocalDateTime cancelReservedAt) {
    }
}
