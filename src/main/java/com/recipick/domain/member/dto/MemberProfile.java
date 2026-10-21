package com.recipick.domain.member.dto;

public record MemberProfile(Long memberId, String email, String name, String phone,
        boolean locationAgreed, Long martId, MemberAddressData address) {

    @Override
    public String toString() {
        return "MemberProfile[REDACTED]";
    }
}
