package com.recipick.domain.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Email @Size(max = 50) String email,
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Size(min = 8, max = 20)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9\\s])[^\\s]+$",
                message = "비밀번호는 영문·숫자·특수문자를 포함해야 합니다.") String password,
        @NotBlank @Pattern(regexp = "[0-9]{10,11}", message = "휴대전화 번호는 숫자 10~11자리여야 합니다.") String phone,
        @NotNull Boolean termsAgreed,
        @NotNull Boolean privacyAgreed
) {
    public SignupRequest {
        if (phone != null) {
            phone = phone.replace("-", "");
        }
    }

    @Override
    public String toString() {
        return "SignupRequest[REDACTED]";
    }
}
