package com.recipick.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MemberWithdrawRequest(@NotBlank @Size(max = 20) String password) {
    @Override
    public String toString() {
        return "MemberWithdrawRequest[REDACTED]";
    }
}
