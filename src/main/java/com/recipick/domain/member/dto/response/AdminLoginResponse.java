package com.recipick.domain.member.dto.response;

public record AdminLoginResponse(
        String accessToken, long accessTokenExpiresIn, Long adminId, String role, Long martId
) {
    @Override
    public String toString() {
        return "AdminLoginResponse[REDACTED]";
    }
}
