package com.recipick.domain.member.dto;

import com.recipick.domain.member.dto.response.AdminLoginResponse;
import java.time.Duration;

public record AdminLoginResult(AdminLoginResponse response, String refreshToken, Duration refreshExpiration) {
    @Override
    public String toString() {
        return "AdminLoginResult[REDACTED]";
    }
}
