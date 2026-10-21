package com.recipick.domain.member.dto.response;

import java.time.LocalDateTime;

public record MemberMeResponse(Long memberId, String email, String name, String phone,
        boolean locationAgreed, Mart mart, CurrentAddress currentAddress,
        MembershipSummary membership, boolean trialEligible) {

    public record Mart(Long martId, String name) {
    }

    public record CurrentAddress(Long addressId, String label, String address, String detailAddress,
            String legalDongCode, boolean serviceAvailable) {
        @Override
        public String toString() {
            return "MemberMeResponse.CurrentAddress[REDACTED]";
        }
    }

    public record MembershipSummary(boolean active, String type, LocalDateTime endAt) {
    }

    @Override
    public String toString() {
        return "MemberMeResponse[REDACTED]";
    }
}
