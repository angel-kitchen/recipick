package com.recipick.global.security;

public record AccessTokenDetails(AuthPrincipal principal, long tokenVersion) {
}
