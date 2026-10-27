package com.recipick.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MemberPasswordChangeRequest(
        @NotBlank @Size(max = 20) String currentPassword,
        @NotBlank @Size(min = 8, max = 20)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9\\s])[^\\s]+$",
                message = "비밀번호는 영문·숫자·특수문자를 포함해야 합니다.") String newPassword
) {
    @Override
    public String toString() {
        return "MemberPasswordChangeRequest[REDACTED]";
    }
}
