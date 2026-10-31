package com.recipick.domain.member.dto;

import java.time.LocalDateTime;

public record MembershipConsent(Long consentedAmount, String consentTextVersion,
        LocalDateTime autopayConsentedAt) {
}
