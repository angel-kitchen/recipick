package com.recipick.domain.member.dto.response;

public record CsrfTokenResponse(String headerName, String token) {
}
