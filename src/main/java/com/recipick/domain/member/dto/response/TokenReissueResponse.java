package com.recipick.domain.member.dto.response;

public record TokenReissueResponse(String accessToken, long accessTokenExpiresIn) {
    @Override
    public String toString() {
        return "TokenReissueResponse[REDACTED]";
    }
}
