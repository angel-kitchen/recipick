package com.recipick.global.security;

public interface SessionStatusChecker {
    void requireActive(AuthPrincipal principal);
}
