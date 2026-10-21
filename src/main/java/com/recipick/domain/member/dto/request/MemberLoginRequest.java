package com.recipick.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.Locale;

public record MemberLoginRequest(@NotBlank String email, @NotBlank String password) {
    public MemberLoginRequest {
        if (email != null) {
            email = email.trim().toLowerCase(Locale.ROOT);
        }
    }

    @Override
    public String toString() {
        return "MemberLoginRequest[REDACTED]";
    }
}
