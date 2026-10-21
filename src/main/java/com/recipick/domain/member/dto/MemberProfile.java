package com.recipick.domain.member.dto;

public record MemberProfile(Long memberId, String email, String name, String phone,
        boolean locationAgreed, Long martId, Address address) {

    public record Address(Long addressId, String label, String address, String detailAddress, String legalDongCode) {
        @Override
        public String toString() {
            return "MemberProfile.Address[REDACTED]";
        }
    }

    @Override
    public String toString() {
        return "MemberProfile[REDACTED]";
    }
}
