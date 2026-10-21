package com.recipick.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record AdminLoginRequest(@NotBlank @Size(max = 50) String loginId, @NotBlank String password) {
    public AdminLoginRequest {
        if (loginId != null) {
            loginId = loginId.trim().toLowerCase(Locale.ROOT);
        }
    }

    @Override
    public String toString() {
        return "AdminLoginRequest[REDACTED]";
    }
}
