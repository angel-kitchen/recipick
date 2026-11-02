package com.recipick.domain.member.dto.request;

import com.recipick.domain.member.dto.MembershipConsent;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.global.exception.BusinessException;
import java.time.LocalDateTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MembershipTrialStartRequest(boolean autopayAgreed, Long consentedAmount,
        @NotBlank @Size(max = 20)
        @Pattern(regexp = "(?U)\\S+", message = "동의 문구 버전은 공백을 포함할 수 없습니다.")
        String consentTextVersion) {
    // 동의 시각은 클라이언트에서 받지 않고 호출자가 정한 서버 시각으로 기록한다.
    public MembershipConsent toConsent(LocalDateTime now) {
        if (!autopayAgreed) {
            throw new BusinessException(MemberErrorCode.AUTOPAY_CONSENT_REQUIRED);
        }
        return new MembershipConsent(consentedAmount, consentTextVersion, now);
    }
}
