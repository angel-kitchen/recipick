package com.recipick.domain.member.dto.response;

import java.time.LocalDateTime;

public record MemberAdminResponse(Long memberId, String email, String name, Long martId,
        boolean membershipActive, String membershipType, LocalDateTime membershipEndAt,
        boolean trialUsed, LocalDateTime createdAt) {

    @Override
    public String toString() {
        return "MemberAdminResponse[REDACTED]";
    }
}
