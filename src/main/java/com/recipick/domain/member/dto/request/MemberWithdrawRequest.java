package com.recipick.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record MemberWithdrawRequest(
        @NotBlank @Size(max = 20) String password,
        @NotNull @PositiveOrZero Long acknowledgedPointBalance
) {
    @Override
    public String toString() {
        return "MemberWithdrawRequest[REDACTED]";
    }
}
