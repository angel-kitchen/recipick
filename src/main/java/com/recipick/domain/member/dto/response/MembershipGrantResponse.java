package com.recipick.domain.member.dto.response;

import java.time.LocalDateTime;

public record MembershipGrantResponse(Long membershipId, Long memberId, String type, String source,
        LocalDateTime startAt, LocalDateTime endAt, Long grantedByAdminId) {
}
