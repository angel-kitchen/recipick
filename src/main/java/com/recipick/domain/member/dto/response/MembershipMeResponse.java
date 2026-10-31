package com.recipick.domain.member.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record MembershipMeResponse(boolean active, Current current, boolean trialEligible, Pending pending) {

    public record Pending(Long membershipId, String type, String source, String status,
            LocalDateTime startAt, LocalDateTime endAt, boolean autoRenew, LocalDateTime cancelReservedAt) {
    }

    public record Current(Long membershipId, String type, String source, LocalDateTime startAt,
            LocalDateTime endAt, boolean autoRenew, LocalDate billingAnchorDate, LocalDateTime cancelReservedAt) {
    }
}
