package com.recipick.domain.member.dto.response;

public record MemberLoginResponse(String accessToken, long accessTokenExpiresIn, Long memberId) {
    @Override
    public String toString() {
        return "MemberLoginResponse[REDACTED]";
    }
}
