package com.recipick.domain.member.dto;

import java.time.LocalDateTime;

public record MemberAdminRow(Long memberId, String email, String name, Long martId, LocalDateTime createdAt) {

    @Override
    public String toString() {
        return "MemberAdminRow[REDACTED]";
    }
}
