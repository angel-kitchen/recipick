package com.recipick.domain.member.dto.request;

import com.recipick.domain.member.dto.MembershipConsent;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.global.exception.BusinessException;
import java.time.LocalDateTime;

public record MembershipTrialStartRequest(boolean autopayAgreed, Long consentedAmount, String consentTextVersion) {
    // 동의 시각은 클라이언트에서 받지 않고 호출자가 정한 서버 시각으로 기록한다.
    public MembershipConsent toConsent(LocalDateTime now) {
        if (!autopayAgreed) {
            throw new BusinessException(MemberErrorCode.AUTOPAY_CONSENT_REQUIRED);
        }
        return new MembershipConsent(consentedAmount, consentTextVersion, now);
    }
}
