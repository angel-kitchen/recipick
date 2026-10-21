package com.recipick.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ServiceAreaCheckRequest(
        @NotBlank @Pattern(regexp = "[0-9]{10}", message = "법정동 코드는 숫자 10자리여야 합니다.") String legalDongCode
) {
}
