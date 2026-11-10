package com.recipick.global.security;

public interface AdminStatusChecker {
    void requireCurrent(AuthPrincipal principal);
}
