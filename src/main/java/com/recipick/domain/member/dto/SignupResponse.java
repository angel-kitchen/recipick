package com.recipick.domain.member.dto;

import com.recipick.domain.member.entity.Member;
import java.time.LocalDateTime;

public record SignupResponse(Long memberId, String email, LocalDateTime createdAt) {

    public static SignupResponse from(Member member) {
        return new SignupResponse(member.getId(), member.getEmail(), member.getCreatedAt());
    }
}
