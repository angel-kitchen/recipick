package com.recipick.domain.member.dto;

import com.recipick.domain.member.dto.response.MemberLoginResponse;
import java.time.Duration;

public record MemberLoginResult(MemberLoginResponse response, String refreshToken, Duration refreshExpiration) {
    @Override
    public String toString() {
        return "MemberLoginResult[REDACTED]";
    }
}
