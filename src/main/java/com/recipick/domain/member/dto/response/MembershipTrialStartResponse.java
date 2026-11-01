package com.recipick.domain.member.dto.response;

import java.time.LocalDateTime;

public record MembershipTrialStartResponse(Long membershipId, String type, String source,
        LocalDateTime startAt, LocalDateTime endAt, boolean autoRenew) {
}
