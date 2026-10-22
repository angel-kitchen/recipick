package com.recipick.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MemberAddressCreateRequest(
        @Size(max = 30) String label,
        @NotBlank @Size(max = 255) String address,
        @NotBlank @Size(max = 255) String detailAddress,
        @NotBlank @Pattern(regexp = "[0-9]{10}", message = "법정동 코드는 숫자 10자리여야 합니다.") String legalDongCode,
        Boolean select,
        Boolean locationAgreed
) {
    @Override
    public String toString() {
        return "MemberAddressCreateRequest[REDACTED]";
    }
}
