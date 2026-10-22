package com.recipick.domain.member.dto;

public record MemberAddressData(Long addressId, String label, String address, String detailAddress,
        String legalDongCode) {

    @Override
    public String toString() {
        return "MemberAddressData[REDACTED]";
    }
}
